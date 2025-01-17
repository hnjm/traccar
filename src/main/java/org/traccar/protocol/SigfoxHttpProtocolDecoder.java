/*
 * Copyright 2017 - 2019 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.DataConverter;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringReader;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class SigfoxHttpProtocolDecoder extends BaseHttpProtocolDecoder {

    public SigfoxHttpProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        FullHttpRequest request = (FullHttpRequest) msg;
        JsonObject json = Json.createReader(new StringReader(URLDecoder.decode(
                request.content().toString(StandardCharsets.UTF_8).split("=")[0], "UTF-8"))).readObject();

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, json.getString("device"));
        if (deviceSession == null) {
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        position.setTime(new Date(json.getInt("time") * 1000L));

        ByteBuf buf = Unpooled.wrappedBuffer(DataConverter.parseHex(json.getString("data")));
        try {
            int type = buf.readUnsignedByte() >> 4;
            if (type == 0) {

                position.setValid(true);
                position.setLatitude(buf.readIntLE() * 0.0000001);
                position.setLongitude(buf.readIntLE() * 0.0000001);
                position.setCourse(buf.readUnsignedByte() * 2);
                position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedByte()));

                position.set(Position.KEY_BATTERY, buf.readUnsignedByte() * 0.025);

            } else {

                getLastLocation(position, position.getDeviceTime());

            }
        } finally {
            buf.release();
        }

        position.set(Position.KEY_RSSI, json.getJsonNumber("rssi").doubleValue());
        position.set(Position.KEY_INDEX, json.getInt("seqNumber"));

        sendResponse(channel, HttpResponseStatus.OK);
        return position;
    }

}
