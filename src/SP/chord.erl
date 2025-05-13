-module(chord).
-export([start/1, join/2, put/3, get/2, stabilize/1, start_tcp_server/1]).

-record(state, {
  id,          % Node's ID
  successor,   % PID of successor node
  predecessor, % PID of predecessor node or nil
  storage,     % Dictionary of key-value pairs
  fingers      % Finger table (simplified as empty list for now)
}).

% Hash function to map keys to the identifier space (0-255)
hash(Key) ->
  erlang:phash2(Key) rem 256.

% Start a new node with a given ID
start(Id) ->
  erlang:put(id, Id),
  State = #state{
    id = Id,
    successor = self(),
    predecessor = nil,
    storage = dict:new(),
    fingers = []
  },
  Pid = spawn(fun() -> loop(State) end),
  spawn(fun() -> stabilize(Pid) end),
  Pid.

% Join an existing ring via a known node
join(Node, ExistingNode) ->
  ExistingNode ! {find_successor, Node, Node, Node#state.id},
  receive
    {successor, Succ} ->
      Node ! {update_successor, Succ}
  end,
  ok.

% Store a key-value pair in the DHT
put(Key, Value, Node) ->
  HashedKey = hash(Key),
  Node ! {find_successor, self(), Node, HashedKey},
  receive
    {successor, Succ} ->
      Succ ! {store, HashedKey, Value}
  end,
  ok.

% Retrieve a value for a given key from the DHT
get(Key, Node) ->
  HashedKey = hash(Key),
  Node ! {find_successor, self(), Node, HashedKey},
  receive
    {successor, Succ} ->
      Succ ! {retrieve, HashedKey, self()},
      receive
        {value, Value} -> Value
      end
  end.

% Stabilization process to maintain the ring
stabilize(Node) ->
  Node ! {stabilize},
  timer:sleep(1000), % Check every second
  stabilize(Node).

% Main loop for node process
loop(State = #state{id = Id}) ->
  SelfId = State#state.id,
  Successor = State#state.successor,
  Predecessor = State#state.predecessor,
  receive
    {update_successor, NewSucc} ->
      loop(State#state{successor = NewSucc});
    {find_successor, Requester, OriginalNode, Id} ->
      SuccessorId = get_id(Successor),
      if
        Id > SelfId andalso Id =< SuccessorId ->
          Requester ! {successor, Successor};
        SelfId > SuccessorId andalso (Id > SelfId orelse Id =< SuccessorId) ->
          Requester ! {successor, Successor};
        true ->
          Successor ! {find_successor, Requester, OriginalNode, Id}
      end,
      loop(State);
    {store, Key, Value} ->
      PredecessorId = case Predecessor of
                        nil -> -1;
                        _ -> get_id(Predecessor)
                      end,
      if
        Key > PredecessorId andalso Key =< SelfId ->
          NewStorage = dict:store(Key, Value, State#state.storage),
          Successor ! {replicate, Key, Value},
          loop(State#state{storage = NewStorage});
        true ->
          Successor ! {store, Key, Value},
          loop(State)
      end;
    {retrieve, Key, Requester} ->
      case dict:find(Key, State#state.storage) of
        {ok, Value} ->
          Requester ! {value, Value};
        error ->
          Successor ! {retrieve, Key, Requester}
      end,
      loop(State);
    {replicate, Key, Value} ->
      NewStorage = dict:store(Key, Value, State#state.storage),
      loop(State#state{storage = NewStorage});
    {stabilize} ->
      Successor ! {get_predecessor, self()},
      loop(State);
    {get_predecessor, Requester} ->
      Requester ! {predecessor, Predecessor},
      loop(State);
    {predecessor, Pred} ->
      SuccessorId = get_id(Successor),
      PredId = case Pred of
                 nil -> -1;
                 _ -> get_id(Pred)
               end,
      if
        Pred =/= nil andalso Pred =/= SelfId andalso
          ((SelfId < SuccessorId andalso PredId > SelfId andalso PredId < SuccessorId) orelse
            (SelfId > SuccessorId andalso (PredId > SelfId orelse PredId < SuccessorId))) ->
          NewState = State#state{successor = Pred},
          Pred ! {notify, self()},
          loop(NewState);
        true ->
          loop(State)
      end;
    {notify, NewPred} ->
      PredId = get_id(NewPred),
      PredecessorId = case Predecessor of
                        nil -> -1;
                        _ -> get_id(Predecessor)
                      end,
      if
        Predecessor == nil orelse
          (PredId > PredecessorId andalso PredId < SelfId) orelse
          (SelfId < PredecessorId andalso (PredId > PredecessorId orelse PredId < SelfId)) ->
          NewState = State#state{predecessor = NewPred},
          % Transfer keys to new predecessor if needed
          KeysToTransfer = dict:filter(fun(K, _) -> K =< PredId end, State#state.storage),
          NewPred ! {replicate_batch, KeysToTransfer},
          NewStorage = dict:filter(fun(K, _) -> K > PredId end, State#state.storage),
          loop(NewState#state{storage = NewStorage});
        true ->
          loop(State)
      end;
    {replicate_batch, Dict} ->
      NewStorage = dict:merge(fun(_, V1, _) -> V1 end, State#state.storage, Dict),
      loop(State#state{storage = NewStorage})
  end.

% Helper function to get the ID of a node
get_id(Node) when Node == self() ->
  erlang:get(id);
get_id(Node) ->
  Node ! {get_id, self()},
  receive
    {id, Id} -> Id
  after 1000 ->
    -1 % Fallback for unreachable nodes
  end.

% Start a TCP server for client interaction
start_tcp_server(Port) ->
  {ok, ListenSocket} = gen_tcp:listen(Port, [binary, {active, false}]),
  spawn(fun() -> accept_loop(ListenSocket) end).

accept_loop(ListenSocket) ->
  {ok, Socket} = gen_tcp:accept(ListenSocket),
  spawn(fun() -> handle_client(Socket) end),
  accept_loop(ListenSocket).

handle_client(Socket) ->
  case gen_tcp:recv(Socket, 0) of
    {ok, Data} ->
      case binary_to_term(Data) of
        {put, Key, Value} ->
          put(Key, Value, self()),
          gen_tcp:send(Socket, term_to_binary(ok));
        {get, Key} ->
          Value = get(Key, self()),
          gen_tcp:send(Socket, term_to_binary(Value))
      end,
      handle_client(Socket);
    {error, closed} ->
      ok
  end.