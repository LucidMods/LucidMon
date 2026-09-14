package com.lucidmon.core;

import java.util.*;

/** Small dependency-free JSON5 subset parser used for LucidMon config/state. */
public final class Json5 {
    private Json5() {}

    public static Object parse(String text) {
        Parser p = new Parser(text == null ? "" : text);
        Object v = p.value();
        p.skip();
        if (!p.end()) throw p.error("Unexpected trailing data");
        return v;
    }

    public static String stringify(Object value) {
        StringBuilder b = new StringBuilder();
        write(value, b, 0);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder b, int indent) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof String s) { quote(s, b); return; }
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Double || v instanceof Float) {
            b.append(v); return;
        }
        if (v instanceof Number n) { b.append(n); return; }
        if (v instanceof Map<?,?> m) {
            b.append("{\n");
            int i = 0;
            for (var e : m.entrySet()) {
                indent(b, indent + 2); quote(String.valueOf(e.getKey()), b); b.append(": ");
                write(e.getValue(), b, indent + 2);
                if (++i < m.size()) b.append(',');
                b.append('\n');
            }
            indent(b, indent); b.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            b.append("[\n");
            List<Object> vals = new ArrayList<>(); it.forEach(vals::add);
            for (int i=0;i<vals.size();i++) {
                indent(b, indent + 2); write(vals.get(i), b, indent + 2);
                if (i + 1 < vals.size()) b.append(',');
                b.append('\n');
            }
            indent(b, indent); b.append(']');
            return;
        }
        quote(String.valueOf(v), b);
    }

    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int)c)); else b.append(c);
                }
            }
        }
        b.append('"');
    }
    private static void indent(StringBuilder b,int n){ b.append(" ".repeat(Math.max(0,n))); }

    private static final class Parser {
        private final String s; private int i;
        Parser(String s){this.s=s;}
        boolean end(){return i>=s.length();}
        RuntimeException error(String m){return new IllegalArgumentException(m+" at character "+i);}

        void skip(){
            while(!end()){
                char c=s.charAt(i);
                if(Character.isWhitespace(c)){i++;continue;}
                if(c=='/' && i+1<s.length()){
                    char n=s.charAt(i+1);
                    if(n=='/'){
                        i+=2; while(!end() && s.charAt(i)!='\n' && s.charAt(i)!='\r') i++; continue;
                    }
                    if(n=='*'){
                        i+=2; boolean closed=false;
                        while(i+1<s.length()){
                            if(s.charAt(i)=='*' && s.charAt(i+1)=='/'){i+=2;closed=true;break;} i++;
                        }
                        if(!closed) throw error("Unterminated block comment");
                        continue;
                    }
                }
                break;
            }
        }

        Object value(){
            skip(); if(end()) throw error("Expected value");
            char c=s.charAt(i);
            if(c=='{') return object();
            if(c=='[') return array();
            if(c=='\'' || c=='"') return string();
            if(c=='-' || c=='+' || c=='.' || Character.isDigit(c)) return number();
            String id=identifier();
            return switch(id){case "true"->Boolean.TRUE;case "false"->Boolean.FALSE;case "null"->null;case "Infinity"->Double.POSITIVE_INFINITY;case "NaN"->Double.NaN;default->id;};
        }

        Map<String,Object> object(){
            LinkedHashMap<String,Object> m=new LinkedHashMap<>(); i++; skip();
            if(peek('}')){i++;return m;}
            while(true){
                skip(); if(end()) throw error("Unterminated object");
                String k=(s.charAt(i)=='\''||s.charAt(i)=='"')?string():keyIdentifier();
                skip(); if(end()||s.charAt(i)!=':') throw error("Expected ':' after key"); i++;
                Object v=value(); m.put(k,v); skip();
                if(peek('}')){i++;break;}
                if(!peek(',')) throw error("Expected ',' or '}'"); i++; skip();
                if(peek('}')){i++;break;}
            }
            return m;
        }

        List<Object> array(){
            ArrayList<Object> a=new ArrayList<>(); i++; skip();
            if(peek(']')){i++;return a;}
            while(true){
                a.add(value()); skip();
                if(peek(']')){i++;break;}
                if(!peek(',')) throw error("Expected ',' or ']'"); i++; skip();
                if(peek(']')){i++;break;}
            }
            return a;
        }

        String string(){
            char q=s.charAt(i++); StringBuilder b=new StringBuilder();
            while(!end()){
                char c=s.charAt(i++); if(c==q) return b.toString();
                if(c=='\\'){
                    if(end()) throw error("Unterminated escape"); char e=s.charAt(i++);
                    switch(e){
                        case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'b'->b.append('\b');case 'f'->b.append('\f');
                        case '\\','\'','"','/'->b.append(e);
                        case 'u'->{
                            if(i+4>s.length()) throw error("Bad unicode escape");
                            b.append((char)Integer.parseInt(s.substring(i,i+4),16)); i+=4;
                        }
                        case '\n'->{ } case '\r'->{ if(!end()&&s.charAt(i)=='\n')i++; }
                        default->b.append(e);
                    }
                } else b.append(c);
            }
            throw error("Unterminated string");
        }

        Number number(){
            int st=i;
            if(peek('+')||peek('-'))i++;
            if(i+1<s.length()&&s.charAt(i)=='0'&&(s.charAt(i+1)=='x'||s.charAt(i+1)=='X')){
                i+=2; int hs=i; while(!end()&&isHex(s.charAt(i)))i++;
                if(hs==i) throw error("Bad hex number"); long n=Long.parseLong(s.substring(hs,i),16);
                return s.charAt(st)=='-'?-n:n;
            }
            boolean dot=false,exp=false;
            while(!end()){
                char c=s.charAt(i);
                if(Character.isDigit(c)){i++;continue;}
                if(c=='.'&&!dot&&!exp){dot=true;i++;continue;}
                if((c=='e'||c=='E')&&!exp){exp=true;i++;if(!end()&&(s.charAt(i)=='+'||s.charAt(i)=='-'))i++;continue;}
                break;
            }
            String n=s.substring(st,i);
            try{
                if(dot||exp) return Double.parseDouble(n);
                long l=Long.parseLong(n); return (l>=Integer.MIN_VALUE&&l<=Integer.MAX_VALUE)?(int)l:l;
            }catch(Exception e){throw error("Bad number '"+n+"'");}
        }

        String keyIdentifier(){
            skip(); int st=i;
            while(!end()){
                char c=s.charAt(i);
                if(Character.isLetterOrDigit(c)||c=='_'||c=='$'||c=='-'||c=='.') i++; else break;
            }
            if(st==i) throw error("Expected object key");
            return s.substring(st,i);
        }

        String identifier(){
            skip(); int st=i;
            while(!end()){
                char c=s.charAt(i);
                if(Character.isLetterOrDigit(c)||c=='_'||c=='$'||c=='-'||c=='.'||c==':'||c=='/' ) i++; else break;
            }
            if(st==i) throw error("Expected identifier");
            return s.substring(st,i);
        }
        boolean peek(char c){return !end()&&s.charAt(i)==c;}
        boolean isHex(char c){return Character.digit(c,16)>=0;}
    }
}
