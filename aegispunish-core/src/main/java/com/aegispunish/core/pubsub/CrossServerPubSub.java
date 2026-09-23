package com.aegispunish.core.pubsub;

import java.util.function.Consumer;

public interface CrossServerPubSub {

    void publish(NetworkPayload payload);

    void subscribe(Consumer<NetworkPayload> listener);
}
