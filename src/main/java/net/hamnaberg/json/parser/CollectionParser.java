/*
 * Copyright 2012 Erlend Hamnaberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.hamnaberg.json.parser;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import net.hamnaberg.json.*;
import net.hamnaberg.json.Collection;
import net.hamnaberg.json.Error;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.util.*;

/**
 * Parser for a vnd.collection+json document.
 */
public class CollectionParser {

    private final JsonFactory factory = new JsonFactory(new ObjectMapper());

    public Collection parse(Reader reader) throws IOException {
        try {
            JsonParser jsonParser = factory.createJsonParser(reader);
            return parse(jsonParser.readValueAsTree());
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Parses a Collection from the given stream.
     * The stream is wrapped in a BufferedReader.
     * <p/>
     * The stream is expected to be UTF-8 encoded.
     *
     * @param stream the stream
     * @return a Collection
     * @throws IOException
     */
    public Collection parse(InputStream stream) throws IOException {
        return parse(new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8)));
    }

    /**
     * Parses a Collection from the given String.
     *
     * @param input the string to parse
     * @return a Collection
     * @throws IOException
     */
    public Collection parse(String input) throws IOException {
        return parse(new StringReader(input));
    }

    public Template parseTemplate(Reader reader) throws IOException {
        try {
            return parseTemplate(factory.createJsonParser(reader).readValueAsTree());
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Parses a JsonCollection from the given stream.
     * The stream is wrapped in a BufferedReader.
     * <p/>
     * The stream is expected to be UTF-8 encoded.
     *
     * @param stream the stream
     * @return a jsonCollection
     * @throws IOException
     */
    public Template parseTemplate(InputStream stream) throws IOException {
        return parseTemplate(new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8)));
    }

    public Template parseTemplate(String input) throws IOException {
        return parseTemplate(new StringReader(input));
    }

    private Collection parse(JsonNode node) throws IOException {
        JsonNode collectionNode = node.get("collection");
        return parseCollection(collectionNode);
    }

    private Collection parseCollection(JsonNode collectionNode) {
        URI href = createURI(collectionNode);
        Version version = getVersion(collectionNode);
        Preconditions.checkArgument(version == Version.ONE, "Version was %s, may only be %s", version.getIdentifier(), Version.ONE.getIdentifier());
        Error error = parseError(collectionNode);

        List<Link> links = parseLinks(collectionNode);
        List<Item> items = parseItems(collectionNode);

        List<Query> queries = parseQueries(collectionNode);
        Template template = parseTemplate(collectionNode);

        return new Collection(href, links, items, queries, template, error);
    }

    private Error parseError(JsonNode collectionNode) {
        JsonNode errorNode = collectionNode.get("error");
        if (errorNode != null) {
            String title = getStringValue(errorNode.get("title"));
            String code = getStringValue(errorNode.get("code"));
            String message = getStringValue(errorNode.get("message"));
            if (isEmpty(title) && isEmpty(code) && isEmpty(message)) {
                return Error.EMPTY;
            }
            return new Error(title, code, message);
        }
        return null;
    }

    private boolean isEmpty(String input) {
        return input == null || input.trim().isEmpty();
    }

    private String getStringValue(JsonNode node) {
        return node == null ? null : node.getTextValue();
    }

    private List<Item> parseItems(JsonNode collectionNode) {
        List<Item> builder = new ArrayList<Item>();
        JsonNode items = collectionNode.get("items");
        if (items != null) {
            for (JsonNode node : items) {
                URI uri = createURI(node);
                builder.add(new Item(uri, parseData(node), parseLinks(node)));
            }
        }
        return builder;
    }

    private List<Query> parseQueries(JsonNode collectionNode) {
        List<Query> builder = new ArrayList<Query>();
        JsonNode queriesNode = collectionNode.get("queries");
        if (queriesNode != null) {
            for (JsonNode node : queriesNode) {
                Link link = toLink(node);
                List<Property> properties = parseData(node);
                builder.add(new Query(link, properties));
            }
        }
        return builder;
    }

    private Template parseTemplate(JsonNode collectionNode) {
        JsonNode node = collectionNode.get("template");
        if (node != null) {
            return new Template(parseData(node));
        }
        return null;
    }

    private List<Property> parseData(JsonNode node) {
        JsonNode data = node.get("data");
        List<Property> builder = new ArrayList<Property>();
        if (data != null) {
            for (JsonNode property : data) {
                builder.add(toProperty(property));
            }
        }
        return builder;
    }

    private Property toProperty(JsonNode node) {
        String name = getStringValue(node.get("name"));
        Optional<String> prompt = Optional.fromNullable(getStringValue(node.get("prompt")));

        Map<String, Value> object = new LinkedHashMap<String, Value>();
        if (node.has("object")) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.get("object").getFields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> i = fields.next();
                Optional<Value> value = ValueFactory.createValue(i.getValue());
                if (value.isPresent()) {
                    object.put(i.getKey(), value.get());
                }
            }
        }
        List<Value> arr = new ArrayList<Value>();
        if (node.has("array")) {
            for (JsonNode i : node.get("array")) {
                Optional<Value> v = ValueFactory.createValue(i);
                if (v.isPresent()) {
                    arr.add(v.get());
                }
            }
        }
        if (!object.isEmpty()) {
            return new Property(name, prompt, object);
        }
        if (!arr.isEmpty()) {
            return new Property(name, prompt, arr);
        }
        Optional<Value> value = ValueFactory.createValue(node.get("value"));
        return new Property(name, prompt, value);
    }

    private URI createURI(JsonNode node) {
        return URI.create(getStringValue(node.get("href")));
    }

    private Version getVersion(JsonNode collectionNode) {
        return Version.getVersion(getStringValue(collectionNode.get("version")));
    }

    private List<Link> parseLinks(JsonNode collectionNode) {
        JsonNode linkCollection = collectionNode.get("links");
        List<Link> linkBuilder = new ArrayList<Link>();
        if (linkCollection != null) {
            for (JsonNode linkNode : linkCollection) {
                linkBuilder.add(toLink(linkNode));
            }
        }
        return linkBuilder;
    }

    private Link toLink(JsonNode linkNode) {
        return new Link(
                createURI(linkNode),
                getStringValue(linkNode.get("rel")),
                Optional.fromNullable(getStringValue(linkNode.get("prompt")))
        );
    }
}
