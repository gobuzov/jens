import files.Sna;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
/* Jens is One class z80 assembler by Arcadiy Gobuzov */
// todo Macros like in Gens
public class Jens{
    public final static int NOPARAM = 255, NUMB = 250, BRACK = 251, IX = 221, IY = 253, IM = 19, RST = 20, LD = 21;
    byte[] mem = new byte[65536]; // memory for compiled bytes
    /// todo may be enough only
    Hashtable<String, Integer> varValues = new Hashtable<>(); // key:name, value: int
    Hashtable<String, Hashtable> modules = new Hashtable<>();// key:module name, value - vars of module
    Hashtable <String, Integer>currModule = null;
    Stack <Hashtable> moduleStack = new Stack();// push Hashtable<String, Integer> // like varValues for every Module
    Stack fileStack = new Stack();// push String[], Integer lineId, cmdId
    int lineId = 0, cmdId = 0; // current Line in curr array, current Command in Line
    String[] curr; // current working file
    Vector<String> commands; // commands made from curr[lineId]
    Vector remember; // for unclosed values, will calc before save binaries. [int ofset, String val, atr[1-8bit, 2-16, 3-dist], errPlace]
    int ofset = 0;//-1;// in byte[] mem
    String currLabel = null; // for Case: LABEL EQU EXPRESSION
    String path, currfile = "default", errors;
    public Jens(String[] s, String path){
        curr = s;
        this.path = path;
    }
    public void compile() {
        System.out.println("Jens v0.01 started");
        Vector<String> com = new Vector<>();
        remember = new Vector(); errors = null;
        try {
            while(true) {
                while (true) {
                    com = getCommand();
                    if (com.isEmpty())
                        break;
                    String s = com.get(0);
                    currLabel = null;
                    if (false == rsv.contains(s)) {
                        if (-1 == ofset)
                            throw new Exception("Declaration before ORG");
                        if (null != varValues.get(s))
                            throw new Exception("Repeated declaration of var ".concat(s));
                        if (1==com.size() || false=="equ".equals(com.get(1)))
                            putVar(s, ofset);
                        currLabel = s;
                        com.remove(0);
                    }
                    if (com.size() > 1 || null == currLabel)
                        doCommand(com);
                } // one file is ended
                if (fileStack.isEmpty())
                    break;
                Object[] file = (Object[]) fileStack.pop();
                curr  = (String[])file[0]; lineId = (Integer)file[1]; cmdId  = (Integer)file[2];
                commands = (Vector<String>)file[3]; currfile = (String)file[4];
            }
        } catch (Exception exc) {
            exc.printStackTrace();
            if (null==errors)
                errors = getErrorPlace(exc.toString());
            System.out.println(errors);
        }
        System.out.println("Jens is Ended");
    }
    Vector<String> getCommand() throws Exception{
        Vector<String> result = new Vector<>();
        do{
            if ((null==commands || 0==commands.size() || cmdId==commands.size()) && lineId!=curr.length) {
                cmdId = 0;
                commands = splitString(cutComments(curr[lineId++]), ":", -1);
            }
            if (cmdId < commands.size())
                result = splitCommand(commands.get(cmdId++));
        }while (result.isEmpty() && lineId!=curr.length);
        return result;// if empty, no more lines and commands in curr[]
    }
    Vector<String> splitString (String s, String delims, int cnt) throws Exception{
        Vector<String> v = new Vector<>();
        s = s.trim();
        if (0==s.length())
            return v;
        if (-1==cnt) // -1 mean total splitting
            cnt = s.length();
        int i=0;
        while (i<s.length() && -1!=delims.indexOf(s.charAt(i)))// skip delims at first positions
            i++;
        if (i==s.length())
            return v;
        int start = i;
        do{
            char ch = s.charAt(i);
            if ('\"'==ch || '\''==ch){ /// Strings or chars can content any delims
                int j=i+1;
                do {
                    if (s.charAt(j)==ch)
                        break;
                }while (++j<s.length());
                if (j==s.length())
                    throw new Exception("Quotes not closed");
                i = j;
            }else if (-1!=delims.indexOf(ch)){
                v.add(s.substring(start, i));
                while (i<s.length()-1 && -1!=delims.indexOf(s.charAt(i+1)))// skip sequence of delims, for example "   "
                    i++;
                if (i==s.length())/// todo check need ???
                    return v;     ///
                start = i+1;
                cnt--;
            }
        }while (++i<s.length() && cnt>0);
        if (start<s.length()){
            String str = s.substring(start, s.length()).trim();
            if (str.length()>0)
                v.add(str);
        }
        for (i=0; i<v.size(); i++){
            s = v.get(i).trim();
            if (s.length()>0 && '\''!=s.charAt(0) && '\"'!=s.charAt(0)) {
                v.set(i, s.toLowerCase());
            }
        }
        return v;
    }
    Vector<String> splitCommand(String s) throws Exception{
        Vector<String> v = splitString(s, ",", -1);
        if (0 == v.size())
            return v;
        Vector<String> w = splitString(v.get(0), "\t ", 1);
        if (false ==  rsv.contains(w.get(0)) && w.size() > 1) { // mean first word is LABEL
            Vector<String> x = splitString(w.get(1), "\t ", 1);
            w.remove(1);
            w.add(x.get(0));
            if (x.size() > 1)
                w.add(x.get(1));
            int i = 1;
            while (i < v.size())
                w.add(v.get(i++).trim());
            return w;
        }
        if (w.size() > 1) {
            int i = 1;
            while (i < v.size())
                w.add(v.get(i++).trim());
            return w;
        }
        return v;
    }
    String getErrorPlace(String errMsg){
        StringBuilder sb = new StringBuilder();
        sb.append("* ** * ** * ** * ** * ** * ** * ** * ** *");
        sb.append(errMsg).append('\n');
        sb.append("File: ").append(currfile).append('\n');
        sb.append("Line number: ").append(lineId).append('\n');
        sb.append("Line string: ").append(curr[lineId-1]).append('\n');
        sb.append("Command: ").append(commands.get(cmdId-1)).append('\n');
        return sb.toString();
    }
    String cutComments(String s) throws Exception{ // todo comments // & /* */
        s = s.trim();
        if (s.length()>0) {
            int i = 0;
            do {
                char ch = s.charAt(i);
                if ('\"' == ch || '\'' == ch) {
                    int j = i + 1;
                    do {
                        if (s.charAt(j) == ch)
                            break;
                    } while (++j < s.length());
                    if (j == s.length())
                        throw new Exception("Quotes not closed");
                    i = j;
                } else if (';' == ch) {
                    return s.substring(0, i);
                }
            } while (++i < s.length());
        }
        return s;
    }
    String cutQuotes(String s){
        if ('"'==s.charAt(0) || '\''==s.charAt(0)){
            s = s.substring(1);
            s = s.substring(0, s.length()-1); // last Quote already checked at cut Comments
        }
        return s;
    }
    void doCommand (Vector<String> cmd) throws Exception {
        String s = cmd.get(0);
        int size = cmd.size();
        if ("include".equals(s)) { // First: check Directives
            if (2 != size)
                throw new Exception("use: INCLUDE FILENAME");
            String fname = cmd.get(1);// todo check repeating file
            //
            String[] arr = readTextFile(cutQuotes(path + cutQuotes(fname)));
            System.out.println("Include " + fname);
            fileStack.push(new Object[]{curr, lineId, cmdId, commands, fname});
            curr = arr;
            commands = null;
            lineId = 0;
            cmdId = 0;
            currfile = fname;
        } else if ("insert".equals(s)) {

        } else if ("device".equals(s)) {

        } else if ("page".equals(s)) {

        } else if ("org".equals(s)) {
            if (2 != size)
                throw new Exception("use: ORG ADDRESS");
            int newofset = calculate(cmd.get(1));
            ofset = newofset;
            System.out.println("ORG " + cmd.get(1) + " " + newofset);
        } else if ("equ".equals(s)) { // only if label
            if (null == currLabel)
                throw new Exception("no LABEL before EQU");
            if (2 != size)
                throw new Exception("use: LABEL EQU VALUE");
            int val = calculate(cmd.get(1));
            putVar(currLabel, val);
        } else if ("module".equals(s)) {
            if (2 != size)
                throw new Exception("use: module NAME");
            String name = cmd.get(1);
            Hashtable<String, Integer> moduleVars = new Hashtable<>();
            modules.put(name, moduleVars); // todo check double modules name, and throw Exception if exists
            if (null != currModule)
                moduleStack.push(currModule);
            currModule = moduleVars;
        } else if ("endmodule".equals(s)) {
            if (null == currModule)
                throw new Exception("endmodule before module");
            currModule = moduleStack.empty() ? null : moduleStack.pop();
        } else if ("if".equals(s)) {
            if (2 != size)
                throw new Exception("use: IF VAR=VALUE");
            Vector<String> x = splitString(cmd.get(1), "=", -1);
            if (2 != x.size())
                throw new Exception("use: IF VAR=VALUE");// again
            int var = getVar(x.get(0));
            int val = getNumber(x.get(1));
            while (var != val) { /// if FALSE, skip all commands before endif
                Vector<String> com = getCommand();
                if (com.isEmpty())
                    throw new Exception("no ENDIF after IF ".concat(cmd.get(1)));
                if ("endif".equals(com.get(0)))
                    break;
            }
        } else if ("phase".equals(s) || "unphase".equals(s) || "endif".equals(s)) { // nothing while

        } else if ("db".equals(s)) {
            for (int i = 1; i < size; i++) {
                s = cmd.get(i);
                if ('"' == s.charAt(0)) { // String
                    for (int j = 0; j < s.length(); j++)
                        mem[ofset++] = (byte) s.charAt(j); // todo Russian letters support, now only ANSI
                } else {
                    int val = calculate(s);
                    if (255 < val)
                        throw new Exception("Value: " + s + " over 8bit");
                    mem[ofset++] = (byte) val;
                }
            }
        }else if ("display".equals(s)){// "TOTAL ",/D, end-start," bytes"
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < size; i++) {
                s = cmd.get(i);
                sb.append('"'==s.charAt(0) ?  cutQuotes(s) : calculate(s));
            }
            System.out.println(sb);
        }else if ("dw".equals(s)) {
            for (int i = 1; i < size; i++) {
                int v = calculate(cmd.get(i));
                mem[ofset++] = (byte)(v&255);
                mem[ofset++] = (byte)(v>>8);
            }
        } else if ("ds".equals(s)) {  //BLOCK 500,0   ; define a block of 500 bytes of zero
            if (size < 2)
                throw new Exception("use: DS LENGTH [BYTE]");
            byte b = size==3 ? Byte.parseByte(cmd.get(2)) : 0;
            int len = Integer.parseInt(cmd.get(1));
            for (int i=0; i<len; i++)
                mem[ofset++] = b;
        } else if ("savebin".equals(s) || "savesna".equals(s)) {// savesna "zz.sna",startpoint
            remember(); // before all save
            if ("savesna".equals(s)) {
                if (size < 3)
                    throw new Exception("use: savesna FILENAME.sna, STARTPOINT [STACKPLACE]");// todo STACKPLACE
                String fname = cmd.get(1);
                int start = calculate(cmd.get(2));
                Sna sna = new Sna();
                sna.write(path + fname, mem, start);
            }else if ("savebin".equals(s)){// todo

            }
        } else {// After all directives: check Assembler command
            // 1.Check params - define prefix(DD,FD), dist(IX+dist), change index to HL stuff
            String s1 = size > 1 ? cmd.get(1) : "";
            String s2 = size > 2 ? cmd.get(2) : "";
            int pref = getPrefix(s1); // [0, DD, FD]
            int dist = 0; // dist(IX+dist)
            if (0 != pref) {
                dist = getDist(s1);
                s1 = getHLString(s1);
            }
            int pref2 = getPrefix(s2);
            if (0 != pref2) {
                if (0 != pref && pref2 != pref) // wrong case: ld (ix+0),ly
                    throw new Exception("Command have both IX and IY!");
                dist = getDist(s2);
                s2 = getHLString(s2);
            }
            boolean needDist = 0 != pref && ("(hl)".equals(s1) || "(hl)".equals(s2));
            int i1 = size > 1 ? getOperand(s1) : NOPARAM;
            int i2 = size > 2 ? getOperand(s2) : NOPARAM;
            int cc = null == cm.get(s) ? -1 : cm.get(s).intValue(); // Command Code
            int ac =0, bc = -1; // Attribute Code (see values in t), Byte Code
            // 2.Check 40h-bf and cbxx commands (have cyclic structure)
/// ((add||adc||sbc) a,[b..a]) || (sub||and||xor||or||cp) [b..a]
            if ((0 == cc || 1 == cc || 3 == cc) && 7 == i1 && i2 < 8 || ((2 == cc || (cc > 3 && cc < 8)) && i1 < 8)) {
                bc = 0x80 + 8 * cc + (2 == cc || (cc > 3 && cc < 8) ? i1 : i2);
            } else if (cc >= 8 && cc < 15 && i1 < 8) {// rlc .. srl todo rlc (ix+d),b
                bc = 0xcb00 + ((cc - 8) * 8 + i1);
            } else if (cc >= 16 && cc <= 18 && (i1 == NUMB && s1.charAt(0) >= '0' && s1.charAt(0) <= '7') && i2 < 8) { // bit, res, set
                bc = 0xcb40 + (cc - 16) * 0x40 + (s1.charAt(0) - '0') * 8 + i2; // todo res 6,(ix+dist),c; but bit 2,(ix+d) for all
            } else if (LD == cc && i1 < 8 && i2 < 8 && false == (i1 == 6 && i2 == 6)) { // ld a,b; false==ld (hl),(hl) is halt
                bc = 0x40 + i1 * 8 + i2;
            } else {
                // todo im, rst cases ???
                StringBuilder sb = new StringBuilder(s);
                s1 = i1 == NUMB ? "n" : i1 == BRACK ? "()" : s1;
                s2 = i2 == NUMB ? "n" : i2 == BRACK ? "()" : s2;
                if (size > 1)
                    sb.append(' ').append(s1);
                if (size > 2)
                    sb.append(',').append(s2);
                String cs = sb.toString();
                Integer cci = t.get(cs); // Command Code
                if (null==cci)
                    throw new Exception("Unknown command :".concat(cs));
                ac = cci>>16;
                bc = cci & 0xffff;
            }
            if (0!=pref)
                mem[ofset++] = (byte)pref; // put [DD,FD] prefix to memory
            pref2 = bc>>8;
            bc &=0xff;
            if (0!=pref2){
                mem[ofset++] = (byte)pref2; // put [CB,ED] prefix to memory
            }
            if (0!=pref && 0xcb==pref2){ // special case: DD CB dist ByteCode
                mem[ofset++] = (byte)dist;
                mem[ofset++] = (byte)bc;
                return;
            }
            mem[ofset++] = (byte)bc;    // after ED, or [DD,FD] second byte; for other first.
            if (needDist)
                mem[ofset++] = (byte)dist;
            if (0==ac) // End of One Byte command (in fact may be 2 or 3 bytes)
                return;
            try {// still ld b,n,0x010006; ld bc,0x020001; djnz n,0x030010;
                s = cmd.get(i1==NUMB || i1==BRACK ? 1:2);
                setBytes(ofset, calculate(s), ac);
            }catch (Exception e){
                if (e instanceof IllegalArgumentException){
                    remember.add(new Object[]{ ofset, s, ac, getErrorPlace(e.toString())});
                }else
                    throw e;
            }
            ofset+= 2==ac? 2 : 1;// add 2 or 1 byte (2 if 16 bits param)
        }
    }
    /// Check not evaluated expressions, store errors if any
    void remember()throws Exception{
        StringBuilder errBuffer = new StringBuilder();
        for(int i=0; i<remember.size(); i++){
            Object[] rec = (Object[]) remember.get(i);
            int ofs  = (Integer) rec[0];
            String expression = (String)  rec[1];
            int atr  = (Integer) rec[2];
            String err = (String)  rec[3];
            try {
                setBytes(ofs, calculate(expression), atr);
            }catch (Exception exc){
                errBuffer.append(err);
            }
        }
        if (false==errBuffer.isEmpty()) {
            errors = errBuffer.toString();
            throw new Exception("Some vars not defined");
        }
    }
    /* Set bytes to memory
     * @param ofs ofset, current as default, and from remember Vector on others cases
     * @param v value (8 or 16 bit)
     * @param ac attribute: 1-8 bits, 2-16 bits, 3-dist for JR, DJNZ
     */
    void setBytes(int ofs, int v, int ac) throws Exception{
        if (1==ac ){
            if (v > 255)
                throw new Exception("Requires 8bit value: " + v);
            mem[ofs] = (byte)v;
        }else if (2==ac){
            mem[ofs++] = (byte)(v&255);
            mem[ofs] = (byte)(v>>8);
        }else if (3==ac){
            v-=ofs+1;
            if (v<-128 || v>127)
                throw new Exception("JR dist out of range: " + v);
            mem[ofs] = (byte)v;
        }
    }
    int getPrefix(String s){
        if (null!=tx.get(s) || s.startsWith("(ix"))
            return IX;
        if (null!=ty.get(s) || s.startsWith("(iy"))
            return IY;
        return 0; // 0 no prefix
    }
    int getOperand(String s){
        Integer i = op.get(s);
        if (null!=i)
            return i;
        return '('==s.charAt(0) ? BRACK : NUMB;
    }
    int getDist(String s){ // dist = getDist(s1); i.e(ix+1) return +1, default 0
        int i=s.indexOf('+');
        if (-1==i)
            i=s.indexOf('-'); // todo check allowed values
        return (-1==i) ? 0 :Integer.parseInt(s.substring(i, s.length()-1));
    }
    String getHLString(String s){// lx->l, iy->hl etc
        if (s.startsWith("(ix") || s.startsWith("(iy"))
            return "(hl)";
        String val = tx.get(s);
        return null!=val? val : ty.get(s);
    }
    public String[] readTextFile(String path) throws Exception {
        Vector<String> v = new Vector<>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line = br.readLine();
        while (line != null) {
            v.add(line);
            //  System.out.println(line);
            line = br.readLine();
        }
        String[] arr = new String[v.size()];
        v.copyInto(arr);
        return arr;
    }
    public int calculate(String s) throws Exception {
        s = s.trim();
        char ch = s.charAt(0);
        if (-1!="+-".indexOf(ch))
            s = "0".concat(s);
        if ('('!=ch)
            s = "(".concat(s).concat(")");
        Stack<Character> ops = new Stack<Character>();
        Stack<Integer> vals = new Stack<Integer>();
        int i = 0;
        while (i < s.length()) {  // Read token, push if operator.
            ch = s.charAt(i++);
            if ('('==ch) {// do nothing
            } else if (-1!="+-*/".indexOf(ch))
                ops.push(ch);
            else if (')'==ch) {  // Pop, evaluate, and push result if token is ")".
                if (ops.empty())
                    break;
                char op = ops.pop();
                int v = vals.pop();
                if ('+'==op)
                    v = vals.pop() + v;
                else if ('-'==op)
                    v = vals.pop() - v;
                else if ('*'==op)
                    v = vals.pop() * v;
                else if ('/'==op)
                    v = vals.pop() / v;
                vals.push(v);
            } else if (' '==ch || '\t'==ch){
                while (i<s.length() && -1!="\t ".indexOf(s.charAt(i)))// skip SPACES
                    i++;
            } else {
                int begin = i-1;
                while (i<s.length() && -1=="(+-*/\t )".indexOf(s.charAt(i)))// check before delimiters
                    i++;
                String token = s.substring(begin, i);// Token not operator or paren: push int value.
                int val = getNumber(token);
                if (-1==val) // mean not number
                    val = getVar(token);
                vals.push(val);
            }
        }
        return vals.pop();
    }
    void putVar(String name, int value){
        if (null!=currModule && '@'!=name.charAt(0))
            currModule.put(name, value);
        else
            varValues.put(name, value);
    }
    int getVar(String name) throws Exception{ // todo getting Module.var
        Integer iv = null;
        if (null!=currModule && '@'!=name.charAt(0))
            iv = currModule.get(name);
        if (null==iv)
            iv = varValues.get(name);
        if (null==iv)
            throw new IllegalArgumentException("Variable ".concat(name).concat(" not initialised before USING"));
        return iv.intValue();
    }
    public Integer getNumber(String s) throws Exception{
        int len = s.length();
        if (0 == len)
            return null;
        char a = s.charAt(0);
        if ('\''==a || '\"'==a){ // case: LD A,"0"-1
            s = cutQuotes(s);
            if (1==s.length())
                return (int)s.charAt(0);
            if (2==s.length())
                return (int)s.charAt(0) + 256*(int)s.charAt(1); // ???todo or 1,0?
            throw new Exception("String as number can have 1 or 2 digits");
        }
        if ('%'==a){ // binary number
            int k = 1;
            int v = 0;
            if (1==len)
                throw new Exception("BINARY numbers too short");
            for (int i = len - 1; i > 0; i--) {
                char ch = s.charAt(i);
                if ('1' == ch)
                    v += k;
                else if ('0'!=ch)
                    throw new Exception("BINARY numbers have only 0 or 1");
                k <<= 1;
            }
            return v;
        }
        if (('$' == a || '#' == a) || ('0' == a && len>1 && 'x' == s.charAt(1))) { // #, $ or 0x - sign of hex number
            s = s.substring('0' == a ? 2 : 1);
            if (0==s.length())
                throw new Exception("HEX numbers too short");
            for (int i=s.length()-1; i>=0; i--){
                    char ch = s.charAt(i);
                    if (ch<'0' || ch>'f' || (ch>'9' && ch <'a'))
                        throw new Exception("HEX numbers have only [0..9] or [a..f]");
            }
            return Integer.parseInt(s, 16);
        }
        for (int i=s.length()-1; i>=0; i--){
            char ch = s.charAt(i);
            if (ch<'0' || ch>'9')
                return -1;
        }
        return Integer.parseInt(s, 10);
    }
    static Hashtable<String, Integer> t = new Hashtable(), cm = new Hashtable(); // commands
    static Hashtable<String, String> tx = new Hashtable(), ty = new Hashtable<>(); // IX, IY alter names
    static Hashtable<String, Integer> op = new Hashtable(); // operands
    static HashSet<String> rsv = new HashSet<>();// Reserved set of all assembler commands names & directives, ie[ld,jp...]
    static {
        t.put("nop",        0);         t.put("ld bc,n",   0x020001);  t.put("ld (bc),a", 2);       t.put("inc bc",    3);
        t.put("inc b",      4);         t.put("dec b",     5);         t.put("ld b,n",  0x010006);  t.put("rlca",      7);
        t.put("ex af,af'",  8);         t.put("add hl,bc", 9);         t.put("ld a,(bc)", 0xa);     t.put("dec bc",    0xb);
        t.put("inc c",      0xc);       t.put("dec c",     0xd);       t.put("ld c,n",  0x01000e);  t.put("rrca",      0xf);
        t.put("djnz n",     0x030010);  t.put("ld de,n",   0x020011);  t.put("ld (de),a", 0x12);    t.put("inc de",    0x13);
        t.put("inc d",      0x14);      t.put("dec d",     0x15);      t.put("ld d,n",  0x010016);  t.put("rla",       0x17);
        t.put("jr n",       0x030018);  t.put("add hl,de", 0x19);      t.put("ld a,(de)", 0x1a);    t.put("dec de",    0x1b);
        t.put("inc e",      0x1c);      t.put("dec e",     0x1d);      t.put("ld e,n",  0x01001e);  t.put("rra",       0x1f);
        t.put("jr nz,n",    0x030020);  t.put("ld hl,n",   0x020021);  t.put("ld (),hl",0x020022);  t.put("inc hl",    0x23);
        t.put("inc h",      0x24);      t.put("dec h",     0x25);      t.put("ld h,n",  0x010026);  t.put("daa",       0x27);
        t.put("jr z,n",     0x030028);  t.put("add hl,hl", 0x29);      t.put("ld hl,()",0x02002a);  t.put("dec hl",    0x2b);
        t.put("inc l",      0x2c);      t.put("dec l",     0x2d);      t.put("ld l,n",  0x01002e);  t.put("cpl",       0x2f);
        t.put("jr nc,n",    0x030030);  t.put("ld sp,n",   0x020031);  t.put("ld (),a", 0x020032);  t.put("inc sp",    0x33);
        t.put("inc (hl)",   0x34);      t.put("dec (hl)",  0x35);      t.put("ld (hl)", 0x010036);  t.put("scf",       0x37);
        t.put("jr c,n",     0x030038);  t.put("add hl,sp", 0x39);      t.put("ld a,()", 0x02003a);  t.put("dec sp",    0x3b);
        t.put("inc a",      0x3c);      t.put("dec a",     0x3d);      t.put("ld a,n",  0x01003e);  t.put("ccf",       0x3f);

        t.put("ret nz",     0xc0);      t.put("pop bc",    0xc1);      t.put("jp nz,n",  0x0200c2); t.put("jp n",     0x0200c3);
        t.put("call nz,n",  0x0200c4);  t.put("push bc",   0xc5);      t.put("add a,n",  0x0100c6); t.put("rst 0",     0xc7);
        t.put("ret z",      0xc8);      t.put("ret",       0xc9);      t.put("jp z,n",   0x0200ca); t.put("exa",       8);
        t.put("call z,n",   0x0200cc);  t.put("call,n",    0x0200cd);  t.put("adc a,n",  0x0100ce); t.put("rst 8",     0xcf);
        t.put("ret nc",     0xd0);      t.put("pop de",    0xd1);      t.put("jp nc,n",  0x0200d2); t.put("out (),a", 0x0100d3);
        t.put("call nc,n",  0x0200d4);  t.put("push de",   0xd5);      t.put("sub n",     0x0100d6);t.put("rst 16",    0xd7);
        t.put("ret c",      0xd8);      t.put("exx",       0xd9);      t.put("jp c,n",   0x0200da); t.put("in a,()",  0x0100db);
        t.put("call c,n",   0x0200dc);  t.put("sbc a,n",   0x0100de);  t.put("jp hl",     0xe9);    t.put("rst 24",    0xdf);
        t.put("ret po",     0xe0);      t.put("pop hl",    0xe1);      t.put("jp po,n",  0x0200e2); t.put("ex (sp),hl",0xe3);
        t.put("call po,n",  0x0200e4);  t.put("push hl",   0xe5);      t.put("and n",     0x0100e6);t.put("rst 32",    0xe7);
        t.put("ret pe",     0xe8);      t.put("jp (hl)",   0xe9);      t.put("jp pe,n",  0x0200ea); t.put("ex de,hl",  0xeb);
        t.put("call pe,n",  0x0200ec);                                 t.put("xor n",     0x0100ee);t.put("rst 40",    0xef);
        t.put("ret p",      0xf0);      t.put("pop af",    0xf1);      t.put("jp p,n",   0x0200f2); t.put("di",        0xf3);
        t.put("call p,n",   0x0200f4);  t.put("push af",   0xf5);      t.put("or n",      0x0100f6);t.put("rst 48",    0xf7);
        t.put("ret m",      0xf8);      t.put("ld sp,hl",  0xf9);      t.put("jp m,n",   0x0200fa); t.put("ei",        0xfb);
        t.put("call m,n",   0x0200fc);  t.put("cp n",      0x0100fe);                               t.put("rst 56",    0xff);

        t.put("in b,(c)",   0xed40);    t.put("out (c),b", 0xed41);    t.put("sbc hl,bc", 0xed42);  t.put("ld (),bc",0x02ed43);
        t.put("neg",        0xed44);    t.put("retn",      0xed45);    t.put("im 0",      0xed46);  t.put("ld i,a",    0xed47);
        t.put("in c,(c)",   0xed48);    t.put("out (c),c", 0xed49);    t.put("adc hl,bc", 0xed4a);  t.put("ld bc,()",0x02ed4b);
                                                                       t.put("reti",      0xed4d);  t.put("ld r,a",    0xed4f);
        t.put("in d,(c)",  0xed50);     t.put("out (c),d", 0xed51);    t.put("sbc hl,de", 0xed52);  t.put("ld (),de",0x02ed53);
                                                                       t.put("im 1",      0xed56);  t.put("ld a,i",    0xed57);
        t.put("in e,(c)",  0xed58);     t.put("out (c),e", 0xed59);    t.put("adc hl,de", 0xed5a);  t.put("ld de,()",0x02ed5b);
                                                                       t.put("im 2",      0xed5e);  t.put("ld a,r",    0xed5f);
        t.put("in h,(c)",  0xed60);     t.put("out (c),h", 0xed61);    t.put("sbc hl,hl", 0xed62);  t.put("ld (),hl",0x02ed63);
                                                                                                     t.put("rrd",       0xed67);
        t.put("in l,(c)",  0xed68);     t.put("out (c),l", 0xed69);    t.put("adc hl,hl", 0xed6a);  t.put("ld hl,()",0x02ed6b);
                                       /* todo */                  t.put("rld",       0xed6f);
        t.put("in (c)",    0xed70);    t.put("out (c),0", 0xed71);    t.put("sbc hl,sp", 0xed72);    t.put("ld (),sp",0x02ed73);
        t.put("in a,(c)",  0xed78);    t.put("out (c),a", 0xed79);    t.put("adc hl,sp", 0xed7a);    t.put("ld sp,()",0x02ed7b);
        t.put("ldi",       0xeda0);    t.put("cpi",       0xeda1);    t.put("ini",       0xeda2);    t.put("outi",      0xeda3);
        t.put("ldd",       0xeda8);    t.put("cpd",       0xeda9);    t.put("ind",       0xedaa);    t.put("outd",      0xedab);
        t.put("ldir",      0xedb0);    t.put("cpir",      0xedb1);    t.put("inir",      0xedb2);    t.put("otir",      0xedb3);
        t.put("lddr",      0xedb8);    t.put("cpdr",      0xedb9);    t.put("indr",      0xedba);    t.put("otdr",      0xedbb);
        t.put("halt",      0x76);
        //
        op.put("b",0);op.put("c",1);op.put("d",2);op.put("e",3);op.put("h",4);op.put("l",5);op.put("(hl)",6);op.put("a",7);
        op.put("af",8);op.put("af'",9);op.put("bc",10);op.put("de",11);op.put("hl",12);op.put("sp",13);op.put("(sp)",14);
        op.put("(bc)",15);op.put("(de)",16);op.put("(c)",17);op.put("nz",18);op.put("nc",19);op.put("po",20);op.put("p",21);
        op.put("pe",22);op.put("m",23);op.put("z",24);op.put("r",25);op.put("i",26);
        //
        tx.put("ix","hl");tx.put("xh","h");tx.put("hx","h");tx.put("xl","l");tx.put("lx","l");
        ty.put("iy","hl");ty.put("yh","h");ty.put("hy","h");ty.put("yl","l");ty.put("ly","l");
        //
        cm.put("add", 0);cm.put("adc",1);cm.put("sub",2);cm.put("sbc",3);cm.put("and",4);cm.put("xor",5);cm.put("or",6);cm.put("cp",7);
        cm.put("rlc",8);cm.put("rrc",9);cm.put("rl",10);cm.put("rr",11);cm.put("sla",12);cm.put("sra",13);cm.put("sll",14);cm.put("srl",15);
        cm.put("bit",16);cm.put("res",17);cm.put("set",18);cm.put("im", IM);cm.put("rst", RST);cm.put("ld", LD);
        Enumeration <String>keys = t.keys();
        while (keys.hasMoreElements()){// for Normal & edXX commands
            String key = keys.nextElement();
            int i = key.indexOf(' ');
            if (-1!=i)
                key = key.substring(0, i);
            rsv.add(key);
        }
        keys = cm.keys();
        while (keys.hasMoreElements()){// for cbXX commands
            rsv.add(keys.nextElement());
        }
        rsv.add("include");rsv.add("insert");rsv.add("device");rsv.add("page");rsv.add("org");rsv.add("module");
        rsv.add("endmodule");rsv.add("savesna");rsv.add("savebin");rsv.add("db");rsv.add("dw");rsv.add("ds");
        rsv.add("equ");rsv.add("if");rsv.add("endif");rsv.add("unphase");rsv.add("phase");rsv.add("display");
        // rsv.add("");rsv.add("");rsv.add("");rsv.add("");rsv.add("");rsv.add("");rsv.add("");rsv.add("");rsv.add("");
    }
}