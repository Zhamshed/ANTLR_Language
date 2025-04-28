grammar Expr;

program: (statement)+;

statement
	: fileAppendStatement											  #fileAppendStmt
    | ';'                                                             # emptyStatement
    | TYPE VAR (',' VAR)* ';'                                         # varDeclStatement
    | expression ';'                                                  # exprStatement
    | 'read' VAR (',' VAR)* ';'                                       # readStatement
    | 'write' expression (',' expression)* ';'                        # writeStatement
    | '{' statement* '}'                                              # blockStatement
    | 'if' '(' condition ')' statement ('else' statement)?            # ifStatement
    | 'while' '(' condition ')' statement                             # whileStatement
    | 'do' statement 'while' '(' condition ')' ';'                    # doWhileStatement
    | 'for' '(' expression ';' condition ';' expression ')' statement # forStatement
    ;
   
fileAppendStatement
    : 'fappend' VAR ',' expression (',' expression)* ';'
    ;


condition
    : expression # boolCondition
    ;

expression
    : '(' expression ')'                                  # parenExpression
    | op='-' expression                                   # negExpression
    | op='!' expression                                   # notExpression
    | expression op=('*' | '/') expression                # aritmExpression
    | expression op='%' expression                        # moduloExpression
    | expression op=('+' | '-') expression                # aritmExpression
    | expression op='.' expression                        # concatExpression
    | expression op=('<' | '>') expression                # relationExpression
    | expression op=('==' | '!=') expression              # comparisonExpression
    | expression op='&&' expression                       # logicExpression
    | expression op='||' expression                       # logicExpression
    | expression '<<' expression                          # fileAppendExpression
    | 'fopen' VAR STRING                                  # fopenExpression
    | VAR                                                 # varExpression
    | literal                                             # literalExpression
    | expression '?' expression split=':' expression      # ternaryExpression
    | VAR '=' expression                                  # assignExpression
    ;

literal
    : INT
    | FLOAT
    | BOOL
    | STRING
    ;

TYPE: 'int' | 'float' | 'bool' | 'string' | 'file';

INT: DIGIT+;
FLOAT: DIGIT+ '.' DIGIT*;
BOOL: 'true' | 'false';
STRING: '"' (ESC | ~["\\])* '"';
VAR: LETTER (LETTER | DIGIT)*;

COMMENT: '//' ~[\r\n]* -> skip;
SPACE: [ \t\r\n]+ -> skip;

fragment ESC: '\\' (["\\/bfnrt] | UNICODE_ESC);
fragment UNICODE_ESC: 'u' HEX HEX HEX HEX;
fragment HEX: [0-9a-fA-F];
fragment LETTER: [a-zA-Z];
fragment DIGIT: [0-9];
