/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.typeconversion

import spock.lang.Specification

class EnumFromCharSequenceNotationParserSpec extends Specification{

    NotationParser<Object, TestEnum> parser = new EnumFromCharSequenceNotationParser(TestEnum.class);

    def "can convert strings to enums"(){
        expect:
        TestEnum.ENUM1 == parser.parseNotation("ENUM1")
        TestEnum.ENUM1 == parser.parseNotation("enum1")
        TestEnum.ENUM1 == parser.parseNotation("EnUm1")
        TestEnum.ENUM2 == parser.parseNotation("enum2")
    }

    def "throws decent error for non convertable strings"(){
        when:
        parser.parseNotation("notKnown")
        then:
        def e = thrown(TypeConversionException)
        e.message == "Cannot coerce string value 'notKnown' to an enum value of type 'org.gradle.internal.typeconversion.EnumFromCharSequenceNotationParserSpec\$TestEnum' (valid case insensitive values: [ENUM1, ENUM2, ENUM3])"
    }

    static enum TestEnum {
        ENUM1, ENUM2, ENUM3
    }

}

