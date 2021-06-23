package com.github.forax.framework.mapper;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.rangeClosed;
import static com.github.forax.framework.mapper.IncompleteJSONParser.Kind.*;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class IncompleteJSONParser {
	enum Kind {
		NULL("(null)"),
		TRUE("(true)"),
		FALSE("(false)"),
		DOUBLE("([0-9]*\\.[0-9]*)"),
		INTEGER("([0-9]+)"),
		STRING("\"([^\\\"]*)\""),
		LEFT_CURLY("(\\{)"),
		RIGHT_CURLY("(\\})"),
		LEFT_BRACKET("(\\[)"),
		RIGHT_BRACKET("(\\])"),
		COLON("(\\:)"),
		COMMA("(\\,)"),
		BLANK("([ \t]+)")
		;

		private final String regex;

		Kind(String regex) {
			this.regex = regex;
		}

		private final static Kind[] VALUES = values();
	}

	private record Token(Kind kind, String text, int location) {
		private boolean is(Kind kind) {
			return this.kind == kind;
		}

		private String expect(Kind kind) {
			if (this.kind != kind) {
				throw error(kind);
			}
			return text;
		}

		public IllegalStateException error(Kind... expectedKinds) {
			return new IllegalStateException("expect " + Arrays.stream(expectedKinds).map(Kind::name).collect(joining(", ")) + " but recognized " + this.kind + " at " + location);
		}
	}

	private record Lexer(Matcher matcher) {
		private Token next() {
			for(;;) {
				if (!matcher.find()) {
					throw new IllegalStateException("no token recognized");
				}
				var index = rangeClosed(1, matcher.groupCount()).filter(i -> matcher.group(i) != null).findFirst().orElseThrow();
				var kind = Kind.VALUES[index - 1];
				if (kind != Kind.BLANK) {
					return new Token(kind, matcher.group(index), matcher.start(index));
				}
			}
		}
	}

	public interface JSONVisitor {
		void key(String key);
		void value(Object value);
    void startObject();
		void endObject();
		void startArray();
		void endArray();
	}

	private static final Pattern PATTERN = compile(Arrays.stream(Kind.VALUES).map(k -> k.regex).collect(joining("|")));

	public static void parse(String input, JSONVisitor visitor) {
		var lexer = new Lexer(PATTERN.matcher(input));
		try {
		  parse(lexer, visitor);
		} catch(IllegalStateException e) {
			throw new IllegalStateException(e.getMessage() + "\n while parsing " + input, e);
		}
	}

	private static void parse(Lexer lexer, JSONVisitor visitor) {
		var token = lexer.next();
		switch(token.kind) {
			case LEFT_CURLY -> {
				visitor.startObject();
				parseObject(lexer, visitor);
			}
			case LEFT_BRACKET -> {
				visitor.startArray();
				parseArray(lexer, visitor);
			}
			default -> throw token.error(LEFT_CURLY, LEFT_BRACKET);
		}
	}

	private static void parseValue(Token token, Lexer lexer, JSONVisitor visitor) {
		switch (token.kind) {
			case NULL -> visitor.value(null);
			case FALSE -> visitor.value(false);
			case TRUE -> visitor.value(true);
			case INTEGER -> visitor.value(parseInt(token.text));
			case DOUBLE -> visitor.value(parseDouble(token.text));
			case STRING -> visitor.value(token.text);
			case LEFT_CURLY -> {
				visitor.startObject();
				parseObject(lexer, visitor);
			}
			case LEFT_BRACKET -> {
				visitor.startArray();
				parseArray(lexer, visitor);
			}
			default -> throw token.error(NULL, FALSE, TRUE, INTEGER, DOUBLE, STRING, LEFT_BRACKET, RIGHT_CURLY);
		}
	}

	private static void parseObject(Lexer lexer, JSONVisitor visitor) {
		var token = lexer.next();
		if (token.is(RIGHT_CURLY)) {
			visitor.endObject();
			return;
		}
		for(;;) {
			var key = token.expect(STRING);
			visitor.key(key);
			lexer.next().expect(COLON);
			token = lexer.next();
			parseValue(token, lexer, visitor);
			token = lexer.next();
			if (token.is(RIGHT_CURLY)) {
				visitor.endObject();
				return;
			}
			token.expect(COMMA);
			token = lexer.next();
		}
	}

	private static void parseArray(Lexer lexer, JSONVisitor visitor) {
		var token = lexer.next();
		if (token.is(RIGHT_BRACKET)) {
			visitor.endArray();
			return;
		}
		for(;;) {
			parseValue(token, lexer, visitor);
			token = lexer.next();
			if (token.is(RIGHT_BRACKET)) {
				visitor.endArray();
				return;
			}
			token.expect(COMMA);
			token = lexer.next();
		}
	}
}