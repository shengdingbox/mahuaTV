package cn.mahua.vod.utils;

import android.content.Context;

import com.blankj.utilcode.util.Utils;
import com.hpplay.sdk.source.api.IConnectListener;
import com.hpplay.sdk.source.api.ILelinkPlayerListener;
import com.hpplay.sdk.source.api.LelinkPlayer;
import com.hpplay.sdk.source.api.LelinkPlayerInfo;
import com.hpplay.sdk.source.browse.api.ILelinkServiceManager;
import com.hpplay.sdk.source.browse.api.LelinkServiceInfo;
import com.hpplay.sdk.source.browse.api.LelinkServiceManager;
import com.hpplay.sdk.source.browse.api.LelinkSetting;

import java.util.List;

public class LelinkHelper {
    private static LelinkHelper sLelinkHelper;
    private Context mContext;
    private LelinkPlayer leLinkPlayer;
    private LelinkServiceInfo lelinkServiceInfo;

    public static LelinkHelper getInstance() {
        if (sLelinkHelper == null) {
            sLelinkHelper = new LelinkHelper(Utils.getApp());
        }
        return sLelinkHelper;
    }

    private LelinkHelper(Context context) {
        mContext = context;
    }

    public void initLelink() {
        LelinkSetting lelinkSetting = new LelinkSetting.LelinkSettingBuilder("15346", "116469a279fa2f4dd69f85dda41b9bb3").build();
        ILelinkServiceManager lelinkServiceManager = LelinkServiceManager.getInstance(mContext);
        lelinkServiceManager.setLelinkSetting(lelinkSetting);
    }

    public void connect(LelinkServiceInfo info, IConnectListener connectListener) {
        lelinkServiceInfo = info;
        leLinkPlayer = new LelinkPlayer(mContext);
        leLinkPlayer.setConnectListener(connectListener);
        leLinkPlayer.connect(info);
    }

    public void disConnect() {
        if (lelinkServiceInfo != null) {
            leLinkPlayer.stop();
            leLinkPlayer.disConnect(lelinkServiceInfo);
            leLinkPlayer.release();
            leLinkPlayer = null;
            lelinkServiceInfo = null;
        }
    }

    public void back(){
        if (lelinkServiceInfo != null) {
            leLinkPlayer.disConnect(lelinkServiceInfo);
            leLinkPlayer.release();
            leLinkPlayer = null;
            lelinkServiceInfo = null;
        }
    }

    public void setPlayerInfo(LelinkPlayerInfo lelinkPlayerInfo) {
        if (leLinkPlayer!=null){
            leLinkPlayer.setDataSource(lelinkPlayerInfo);
        }
    }


    public void setLelinkPlayerListener(ILelinkPlayerListener playerListener) {
        if (leLinkPlayer!=null) {
            leLinkPlayer.setPlayerListener(playerListener);
        }
    }

    public void start() {
        leLinkPlayer.start();
    }

    public void stop() {
        leLinkPlayer.stop();
    }

    public void pause() {
        leLinkPlayer.pause();
    }

    public void resume() {
        leLinkPlayer.resume();
    }

    public List<LelinkServiceInfo> getConnectLelinkServiceInfos() {
        if (leLinkPlayer != null)
            return leLinkPlayer.getConnectLelinkServiceInfos();
        else
            return null;
    }
}
