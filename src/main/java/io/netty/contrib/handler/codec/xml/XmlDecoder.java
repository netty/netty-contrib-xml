/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.handler.codec.xml;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/**
 * Async XML decoder based on <a href="https://github.com/FasterXML/aalto-xml">Aalto XML parser</a>.
 *
 * Parses the incoming data into one of XML messages defined in this package.
 */

public class XmlDecoder extends ByteToMessageDecoder {

    private static final AsyncXMLInputFactory XML_INPUT_FACTORY = new InputFactoryImpl();
    private static final XmlDocumentEnd XML_DOCUMENT_END = XmlDocumentEnd.INSTANCE;

    private final AsyncXMLStreamReader<AsyncByteArrayFeeder> streamReader = XML_INPUT_FACTORY.createAsyncForByteArray();
    private final AsyncByteArrayFeeder streamFeeder = streamReader.getInputFeeder();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        byte[] buffer = new byte[in.readableBytes()];
        in.readBytes(buffer);
        try {
            streamFeeder.feedInput(buffer, 0, buffer.length);
        } catch (XMLStreamException exception) {
            in.skipBytes(in.readableBytes());
            throw exception;
        }

        while (!streamFeeder.needMoreInput()) {
            int type = streamReader.next();
            switch (type) {
                case XMLStreamConstants.START_DOCUMENT:
                    ctx.fireChannelRead(new XmlDocumentStart(streamReader.getEncoding(), streamReader.getVersion(),
                            streamReader.isStandalone(), streamReader.getCharacterEncodingScheme()));
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    ctx.fireChannelRead(XML_DOCUMENT_END);
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    XmlElementStart elementStart = new XmlElementStart(streamReader.getLocalName(),
                            streamReader.getName().getNamespaceURI(), streamReader.getPrefix());
                    for (int x = 0; x < streamReader.getAttributeCount(); x++) {
                        XmlAttribute attribute = new XmlAttribute(streamReader.getAttributeType(x),
                                streamReader.getAttributeLocalName(x), streamReader.getAttributePrefix(x),
                                streamReader.getAttributeNamespace(x), streamReader.getAttributeValue(x));
                        elementStart.attributes().add(attribute);
                    }
                    for (int x = 0; x < streamReader.getNamespaceCount(); x++) {
                        XmlNamespace namespace = new XmlNamespace(streamReader.getNamespacePrefix(x),
                                streamReader.getNamespaceURI(x));
                        elementStart.namespaces().add(namespace);
                    }
                    ctx.fireChannelRead(elementStart);
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    XmlElementEnd elementEnd = new XmlElementEnd(streamReader.getLocalName(),
                            streamReader.getName().getNamespaceURI(), streamReader.getPrefix());
                    for (int x = 0; x < streamReader.getNamespaceCount(); x++) {
                        XmlNamespace namespace = new XmlNamespace(streamReader.getNamespacePrefix(x),
                                streamReader.getNamespaceURI(x));
                        elementEnd.namespaces().add(namespace);
                    }
                    ctx.fireChannelRead(elementEnd);
                    break;
                case XMLStreamConstants.PROCESSING_INSTRUCTION:
                    ctx.fireChannelRead(
                            new XmlProcessingInstruction(streamReader.getPIData(), streamReader.getPITarget()));
                    break;
                case XMLStreamConstants.CHARACTERS:
                    ctx.fireChannelRead(new XmlCharacters(streamReader.getText()));
                    break;
                case XMLStreamConstants.COMMENT:
                    ctx.fireChannelRead(new XmlComment(streamReader.getText()));
                    break;
                case XMLStreamConstants.SPACE:
                    ctx.fireChannelRead(new XmlSpace(streamReader.getText()));
                    break;
                case XMLStreamConstants.ENTITY_REFERENCE:
                    ctx.fireChannelRead(new XmlEntityReference(streamReader.getLocalName(), streamReader.getText()));
                    break;
                case XMLStreamConstants.DTD:
                    ctx.fireChannelRead(new XmlDTD(streamReader.getText()));
                    break;
                case XMLStreamConstants.CDATA:
                    ctx.fireChannelRead(new XmlCdata(streamReader.getText()));
                    break;
            }
        }
    }

}
