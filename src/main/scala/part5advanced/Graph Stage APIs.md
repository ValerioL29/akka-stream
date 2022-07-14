# Graph Stage APIs

## Input port Methods

#### `InHandler`s interact with the upstream

* `onPush`
* `onUpstreamFinish`
* `onUpstreamFailure`

####  Input ports can check and retrieve elements

* `pull`: signal demand
* `grab`: take an element
* `cancel`: tell upstream to stop
* `isAvailable`
* `hasBeenPulled`
* `isClosed`

## Output port Methods

#### `OutHandler`s interact with downstream

* `onPull`
* `onDownstreamFinish`

> no `onDownstreamFailure` as I will receive a cancel signal

#### Output ports can send elements

* `push`: send an element
* `complete`: finish the stream
* `fail`
* `isAvailable`
* `isClosed`