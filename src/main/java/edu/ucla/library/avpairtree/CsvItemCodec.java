
package edu.ucla.library.avpairtree;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

/**
 * A codec that allows passing CsvItem(s) over the Vert.x event bus.
 */
public class CsvItemCodec implements MessageCodec<CsvItem, CsvItem> {

    @Override
    public void encodeToWire(final Buffer aBuffer, final CsvItem aCsvItem) {
        final JsonObject jsonItem = aCsvItem.toJSON();
        final String message = jsonItem.encode();

        aBuffer.appendInt(message.getBytes().length);
        aBuffer.appendString(message);
    }

    @Override
    public CsvItem decodeFromWire(final int aPosition, final Buffer aBuffer) {
        final int length = aBuffer.getInt(aPosition);
        int position = aPosition;

        // Skip 4 because getInt() equals four bytes
        return CsvItem.fromString(aBuffer.getString(position += 4, position += length));
    }

    @Override
    public CsvItem transform(final CsvItem aCsvItem) {
        return aCsvItem;
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }

}
