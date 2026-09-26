package app.mdreader.mobile;

public final class SearchMatchStateSmokeTest {
    private static int tests;
    private static void expect(String source,String query,boolean matchCase,int anchor,int direction,int count,int ordinal,int offset){
        SearchMatchState s=SearchMatchState.scan(source,query,matchCase,anchor,direction);tests++;
        if(s.count!=count||s.ordinal!=ordinal||s.offset!=offset)throw new AssertionError("case "+tests+": "+s.count+","+s.ordinal+","+s.offset);
    }
    public static void main(String[] args){
        expect("Q1 x Q1","Q1",true,0,-1,2,2,5); // previous from first wraps, not first again
        expect("Q1 x Q1","Q1",true,7,1,2,1,0);
        expect("Q1 x Q1","Q1",true,2,1,2,2,5);
        expect("Q1 q1 Q1","Q1",true,0,1,2,1,0);
        expect("Q1 q1 Q1","Q1",false,2,1,3,2,3);
        expect("aaa","aa",true,1,1,1,1,0); // same non-overlap rule as replace all
        expect("ألف\nباء ثم ألف\nباء","ألف\nباء",true,0,-1,2,2,11);
        expect("🙂 x 🙂","🙂",true,2,1,2,2,5); // UTF-16 offsets
        expect("A","",false,0,1,0,0,-1);
        expect("A","missing",false,0,-1,0,0,-1);
        expect(null,"x",true,0,1,0,0,-1);
        expect("Q1 x Q1","Q1",true,4,0,2,1,0);
        StringBuilder huge=new StringBuilder();for(int i=0;i<20000;i++)huge.append("Q1 ");
        expect(huge.toString(),"Q1",true,0,-1,20000,20000,59997);
        // Cross-check count against the established engine over many anchors and cases.
        String source="Aa aa AA أ ب أ ب 🙂🙂";
        for(String q:new String[]{"a","aa","أ ب","🙂"," ","missing"})for(boolean c:new boolean[]{true,false})for(int anchor=0;anchor<=source.length();anchor++){
            SearchMatchState state=SearchMatchState.scan(source,q,c,anchor,1);tests++;
            if(state.count!=SearchReplaceEngine.count(source,q,c))throw new AssertionError("count mismatch");
            if(state.offset>=0&&!SearchReplaceEngine.matchesAt(source,q,state.offset,c))throw new AssertionError("invalid match");
        }
        System.out.println("Search match state: "+tests+" checks passed.");
    }
}
