package app.mdreader.mobile;

/** A single pass over non-overlapping literal matches. No list or artificial result limit. */
final class SearchMatchState {
    final int count, ordinal, offset;
    private SearchMatchState(int count,int ordinal,int offset){this.count=count;this.ordinal=ordinal;this.offset=offset;}
    // direction: +1 next at/after anchor, -1 previous strictly before anchor, 0 current at/before anchor.
    static SearchMatchState scan(String text,String query,boolean matchCase,int anchor,int direction){
        if(text==null||query==null||query.isEmpty())return new SearchMatchState(0,0,-1);
        int count=0,first=-1,last=-1,chosen=-1,ordinal=0;
        for(int from=0;;){
            int at=SearchReplaceEngine.findNext(text,query,from,matchCase);
            if(at<0)break;
            count++;if(first<0)first=at;last=at;
            if(direction>0){if(chosen<0&&at>=anchor){chosen=at;ordinal=count;}}
            else if(direction<0){if(at<anchor){chosen=at;ordinal=count;}}
            else if(at<=anchor){chosen=at;ordinal=count;}
            from=at+query.length();
            if(from>text.length())break;
        }
        if(chosen<0&&count>0){chosen=direction<0?last:first;ordinal=direction<0?count:1;}
        return new SearchMatchState(count,ordinal,chosen);
    }
}
