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

package net.hamnaberg.json.generator;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import net.hamnaberg.json.Property;
import net.hamnaberg.json.Template;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;


/**
 * @author Erlend Hamnaberg<erlend@hamnaberg.net>
 */
public class TemplateGenerator extends AbstractGenerator<Template> {
    private final PropertyGenerator propertyGenerator = new PropertyGenerator();

    public TemplateGenerator() {
    }

    @Override
    public JsonNode toNode(Template object) {
        ObjectNode node = nodeFactory.objectNode();

        node.put("data", createArray(object.getProperties(), new Function<Property, JsonNode>() {
            @Override
            public JsonNode apply(Property input) {
                return propertyGenerator.toNode(input);
            }
        }));
        return node;
    }
}
