package cn.mahua.vod.jiexi;

public interface BackListener {

    void onSuccess(String url,int curParseIndex);

    void onError();

    void onProgressUpdate(String msg);
}
