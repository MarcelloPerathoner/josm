// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.template_engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.search.SearchParseError;
import org.openstreetmap.josm.testutils.DatasetFactory;

/**
 * Unit tests of {@link TemplateParser} class.
 */
class TemplateParserTest {
    /**
     * Test to parse an empty string.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testEmpty() throws ParseError {
        TemplateParser parser = new TemplateParser("");
        assertEquals(new StaticText(""), parser.parse());
    }

    /**
     * Test to parse a variable.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testVariable() throws ParseError {
        TemplateParser parser = new TemplateParser("abc{var}\\{ef\\$\\{g");
        assertEquals(CompoundTemplateEntry.fromArray(new StaticText("abc"),
                new Variable("var"), new StaticText("{ef${g")), parser.parse());
    }

    /**
     * Test to parse a condition with whitespaces.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testConditionWhitespace() throws ParseError {
        TemplateParser parser = new TemplateParser("?{ '{name} {desc}' | '{name}' | '{desc}'    }");
        Condition condition = new Condition(Arrays.asList(
            CompoundTemplateEntry.fromArray(new Variable("name"), new StaticText(" "), new Variable("desc")),
            new Variable("name"),
            new Variable("desc")));
        assertEquals(condition, parser.parse());
    }

    /**
     * Test to parse a condition without whitespace.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testConditionNoWhitespace() throws ParseError {
        TemplateParser parser = new TemplateParser("?{'{name} {desc}'|'{name}'|'{desc}'}");
        Condition condition = new Condition(Arrays.asList(
                CompoundTemplateEntry.fromArray(new Variable("name"), new StaticText(" "), new Variable("desc")),
                new Variable("name"),
                new Variable("desc")));
        assertEquals(condition, parser.parse());
    }

    private static Match compile(String expression) throws SearchParseError {
        return SearchCompiler.compile(expression);
    }

    /**
     * Test to parse a search expression condition.
     * @throws ParseError if the template cannot be parsed
     * @throws SearchParseError if an error has been encountered while compiling
     */
    @Test
    void testConditionSearchExpression() throws ParseError, SearchParseError {
        TemplateParser parser = new TemplateParser("?{ admin_level = 2 'NUTS 1' | admin_level = 4 'NUTS 2' |  '{admin_level}'}");
        Condition condition = new Condition(Arrays.asList(
                new SearchExpressionCondition(compile("admin_level = 2"), new StaticText("NUTS 1")),
                new SearchExpressionCondition(compile("admin_level = 4"), new StaticText("NUTS 2")),
                new Variable("admin_level")));
        TemplateEntry parse = parser.parse();
        assertEquals(condition, parse);
    }

    TemplateEngineDataProvider dataProvider = new TemplateEngineDataProvider() {
        Map<String, String> tags = new HashMap<String, String>() {{
            put("name", "waypointName");
            put("number", "10");
            put("description", "Cycleway");
            put("description:de", "Fahrradweg");
            put("description:de_CH", "Veloweg");
            put("special:key", "specialKey");
        }};

        @Override
        public Object getTemplateValue(String name, boolean special) {
            if (special) {
                if ("localName".equals(name))
                    return "localName";
                else
                    return null;
            } else {
                return tags.get(name);
            }
        }

        @Override
        public boolean evaluateCondition(Match condition) {
            return true;
        }

        @Override
        public List<String> getTemplateKeys() {
            return new ArrayList<>(tags.keySet());
        }
    };

    /**
     * Parse template against dataprovider and check result
     * @param template The template to parse
     * @param result   The expected result
     * @throws ParseError if the template cannot be parsed
     */
    private void assert_equals(String template, String result) throws ParseError {
        TemplateParser parser = new TemplateParser(template);
        TemplateEntry templateEntry = parser.parse();
        StringBuilder sb = new StringBuilder();
        templateEntry.appendText(sb, dataProvider);
        assertEquals(result, sb.toString());
    }

    /**
     * Test to fill a template.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testFilling() throws ParseError {
        TemplateParser parser = new TemplateParser("{name} u{unknown}u i{number}i");
        TemplateEntry entry = parser.parse();
        StringBuilder sb = new StringBuilder();
        entry.appendText(sb, dataProvider);
        assertEquals("waypointName uu i10i", sb.toString());
    }

    /**
     * Test to parse a search expression.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testFillingSearchExpression() throws ParseError {
        TemplateParser parser = new TemplateParser("?{ admin_level = 2 'NUTS 1' | admin_level = 4 'NUTS 2' |  '{admin_level}'}");
        TemplateEntry templateEntry = parser.parse();

        StringBuilder sb = new StringBuilder();
        Relation r = new Relation();
        r.put("admin_level", "2");
        templateEntry.appendText(sb, r);
        assertEquals("NUTS 1", sb.toString());

        sb.setLength(0);
        r.put("admin_level", "5");
        templateEntry.appendText(sb, r);
        assertEquals("5", sb.toString());
    }

    /**
     * Test to print all.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testPrintAll() throws ParseError {
        TemplateParser parser = new TemplateParser("{special:everything}");
        TemplateEntry entry = parser.parse();
        assertEquals("{special:everything}", entry.toString());
        StringBuilder sb = new StringBuilder();
        entry.appendText(sb, dataProvider);
        assertEquals(
            "description=Cycleway, description:de=Fahrradweg, description:de_CH=Veloweg, name=waypointName, number=10, special:key=specialKey",
            sb.toString()
        );
    }

    /**
     * Test to print on several lines.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testPrintMultiline() throws ParseError {
        TemplateParser parser = new TemplateParser("{name}\\n{number}");
        TemplateEntry entry = parser.parse();
        StringBuilder sb = new StringBuilder();
        entry.appendText(sb, dataProvider);
        assertEquals("waypointName\n10", sb.toString());
    }

    /**
     * Test to print special variables.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testSpecialVariable() throws ParseError {
        assert_equals("{name}u{special:localName}u{special:special:key}", "waypointNameulocalNameuspecialKey");
    }

    /**
     * Test special:local variables.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testSpecialLocalVariable() throws ParseError {
        Locale old_locale = Locale.getDefault();

        Locale.setDefault(Locale.forLanguageTag("de-DE"));
        assert_equals("{special:local:description}", "Fahrradweg");

        Locale.setDefault(Locale.forLanguageTag("de-AT"));
        assert_equals("{special:local:description}", "Fahrradweg");

        Locale.setDefault(Locale.forLanguageTag("de-CH"));
        assert_equals("{special:local:description}", "Veloweg");

        Locale.setDefault(Locale.forLanguageTag("fr-FR")); // default to unlocalized description
        assert_equals("{special:local:description}", "Cycleway");

        Locale.setDefault(old_locale);
        assert_equals("{special:local:nosuchtag}", "");
        assert_equals("{special:local:number}", "10");
    }

    /**
     * Test special:local variables in conditions.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testSpecialLocalVariableImplicitCondition() throws ParseError {
        Locale old_locale = Locale.getDefault();

        Locale.setDefault(Locale.forLanguageTag("de-DE"));
        assert_equals("?{'{special:local:description}' | 'y'}", "Fahrradweg");
        assert_equals("?{'{special:local:nosuchtag}'   | 'y'}", "y");

        Locale.setDefault(old_locale);
        assert_equals("?{'x{special:local:nosuchtag}'  | 'y'}", "y");
        assert_equals("?{'x{special:local:number}'     | 'y'}", "x10");
    }

    @Test
    void testSearchExpression() throws Exception {
        compile("(parent type=type1 type=parent1) | (parent type=type2 type=parent2)");
        //"parent(type=type1,type=parent1) | (parent(type=type2,type=parent2)"
        //TODO
    }

    /**
     * Test to switch context.
     * @throws ParseError if the template cannot be parsed
     */
    @Test
    void testSwitchContext() throws ParseError {
        TemplateParser parser = new TemplateParser("!{parent() type=parent2 '{name}'}");
        DatasetFactory ds = new DatasetFactory();
        Relation parent1 = ds.addRelation(1);
        parent1.put("type", "parent1");
        parent1.put("name", "name_parent1");
        Relation parent2 = ds.addRelation(2);
        parent2.put("type", "parent2");
        parent2.put("name", "name_parent2");
        Node child = ds.addNode(1);
        parent1.addMember(new RelationMember("", child));
        parent2.addMember(new RelationMember("", child));

        StringBuilder sb = new StringBuilder();
        TemplateEntry entry = parser.parse();
        entry.appendText(sb, child);

        assertEquals("name_parent2", sb.toString());
    }

    @Test
    void testSetAnd() throws ParseError {
        TemplateParser parser = new TemplateParser("!{(parent(type=child) type=parent) & (parent type=child subtype=parent) '{name}'}");
        DatasetFactory ds = new DatasetFactory();
        Relation parent1 = ds.addRelation(1);
        parent1.put("type", "parent");
        parent1.put("subtype", "parent");
        parent1.put("name", "name_parent1");
        Node child = ds.addNode(1);
        child.put("type", "child");
        parent1.addMember(new RelationMember("", child));

        StringBuilder sb = new StringBuilder();
        TemplateEntry entry = parser.parse();
        entry.appendText(sb, child);

        assertEquals("name_parent1", sb.toString());
    }

    @Test
    void testSetOr() throws ParseError {
        TemplateParser parser = new TemplateParser("!{(parent(type=type1) type=parent1) | (parent type=type2 type=parent2) '{name}'}");
        DatasetFactory ds = new DatasetFactory();
        Relation parent1 = ds.addRelation(1);
        parent1.put("type", "parent1");
        parent1.put("name", "name_parent1");
        Relation parent2 = ds.addRelation(2);
        parent2.put("type", "parent2");
        parent2.put("name", "name_parent2");
        Node child1 = ds.addNode(1);
        child1.put("type", "type1");
        parent1.addMember(new RelationMember("", child1));
        parent2.addMember(new RelationMember("", child1));
        Node child2 = ds.addNode(2);
        child2.put("type", "type2");
        parent1.addMember(new RelationMember("", child2));
        parent2.addMember(new RelationMember("", child2));

        StringBuilder sb = new StringBuilder();
        TemplateEntry entry = parser.parse();
        entry.appendText(sb, child1);
        entry.appendText(sb, child2);

        assertEquals("name_parent1name_parent2", sb.toString());
    }

    @Test
    void testMultilevel() throws ParseError {
        TemplateParser parser = new TemplateParser(
                "!{(parent(parent(type=type1)) type=grandparent) | (parent type=type2 type=parent2) '{name}'}");
        DatasetFactory ds = new DatasetFactory();
        Relation parent1 = ds.addRelation(1);
        parent1.put("type", "parent1");
        parent1.put("name", "name_parent1");
        Relation parent2 = ds.addRelation(2);
        parent2.put("type", "parent2");
        parent2.put("name", "name_parent2");
        Node child1 = ds.addNode(1);
        child1.put("type", "type1");
        parent1.addMember(new RelationMember("", child1));
        parent2.addMember(new RelationMember("", child1));
        Node child2 = ds.addNode(2);
        child2.put("type", "type2");
        parent1.addMember(new RelationMember("", child2));
        parent2.addMember(new RelationMember("", child2));
        Relation grandParent = ds.addRelation(3);
        grandParent.put("type", "grandparent");
        grandParent.put("name", "grandparent_name");
        grandParent.addMember(new RelationMember("", parent1));


        StringBuilder sb = new StringBuilder();
        TemplateEntry entry = parser.parse();
        entry.appendText(sb, child1);
        entry.appendText(sb, child2);

        assertEquals("grandparent_namename_parent2", sb.toString());
    }

    @Test
    void testErrorsNot() {
        TemplateParser parser = new TemplateParser("!{-parent() '{name}'}");
        assertThrows(ParseError.class, parser::parse);
    }

    @Test
    void testErrorOr() {
        TemplateParser parser = new TemplateParser("!{parent() | type=type1 '{name}'}");
        assertThrows(ParseError.class, parser::parse);
    }

    @Test
    void testChild() throws ParseError {
        TemplateParser parser = new TemplateParser("!{((child(type=type1) type=child1) | (child type=type2 type=child2)) type=child2 '{name}'}");
        DatasetFactory ds = new DatasetFactory();
        Relation parent1 = ds.addRelation(1);
        parent1.put("type", "type1");
        Relation parent2 = ds.addRelation(2);
        parent2.put("type", "type2");
        Node child1 = ds.addNode(1);
        child1.put("type", "child1");
        child1.put("name", "child1");
        parent1.addMember(new RelationMember("", child1));
        parent2.addMember(new RelationMember("", child1));
        Node child2 = ds.addNode(2);
        child2.put("type", "child2");
        child2.put("name", "child2");
        parent1.addMember(new RelationMember("", child2));
        parent2.addMember(new RelationMember("", child2));

        StringBuilder sb = new StringBuilder();
        TemplateEntry entry = parser.parse();
        entry.appendText(sb, parent2);

        assertEquals("child2", sb.toString());
    }

    @Test
    void testToStringCanBeParsedAgain() throws Exception {
        final String s1 = "?{ '{name} ({desc})' | '{name} ({cmt})' | '{name}' | '{desc}' | '{cmt}' }";
        final String s2 = new TemplateParser(s1).parse().toString();
        final String s3 = new TemplateParser(s2).parse().toString();
        assertEquals(s1, s2);
        assertEquals(s2, s3);
    }
}
