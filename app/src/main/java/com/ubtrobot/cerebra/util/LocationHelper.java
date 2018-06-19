package com.ubtrobot.cerebra.util;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.ubtrobot.cerebra.other.CerebraApp;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;

/**
 * Provide location from Tencent LBS.
 */

public class LocationHelper implements TencentLocationListener {

    private static final Logger LOGGER = ULog.getLogger("LocationHelper");

    private final static String CURRENT_LOCATION = "CURRENT_LOCATION";
    private final static String DEFAULT_LOCATION = "深圳市";

    private String mLocation;
    private TencentLocationManager mLocationManager;


    private LocationHelper() {
        mLocation = SharePreferenceUtil.getParam(CerebraApp.getInstance(),
                CURRENT_LOCATION, DEFAULT_LOCATION).toString();
    }

    private static LocationHelper mLocationHelper;

    public static LocationHelper getInstance() {
        if (mLocationHelper == null) {
            synchronized (LocationHelper.class) {
                if (mLocationHelper == null) {
                    mLocationHelper = new LocationHelper();
                }
            }
        }

        return mLocationHelper;
    }

    public String getLocation() {
        return mLocation;
    }

    public void requestLocation() {
        mLocationManager = TencentLocationManager
                .getInstance(CerebraApp.getInstance());

        // 设置坐标系为 gcj-02, 缺省坐标为 gcj-02, 所以通常不必进行如下调用
        mLocationManager.setCoordinateType(TencentLocationManager.COORDINATE_TYPE_GCJ02);
        TencentLocationRequest request
                = TencentLocationRequest
                .create()
                .setRequestLevel(TencentLocationRequest.REQUEST_LEVEL_ADMIN_AREA)
                .setInterval(60000); // 设置定位周期, 建议值为 1s-20s

        // 开始定位
        int code = mLocationManager.requestLocationUpdates(request, this);
        LOGGER.i("request location, code: " + code);
    }

    private void save(TencentLocation location) {
        String city = location.getCity();
        LOGGER.i("实时获取=" + city);
        SharePreferenceUtil.setParam(CerebraApp.getInstance(), CURRENT_LOCATION, city);

    }

    @Override
    public void onLocationChanged(TencentLocation location, int error, String s) {
        LOGGER.i("onLocationChanged");
        if (error == TencentLocation.ERROR_OK) {
            // 定位成功
            StringBuilder sb = new StringBuilder();
            sb.append("(纬度=")
                    .append(location.getLatitude())
                    .append(",经度=")
                    .append(location.getLongitude())
                    .append(",精度=")
                    .append(location.getAccuracy())
                    .append("), 来源=")
                    .append(location.getProvider())
                    .append(", 城市=")
                    .append(location.getCity())
                    .append(",citycode=")
                    .append(location.getCityCode());

            save(location);

            mLocationManager.removeUpdates(this);

            LOGGER.i(sb.toString());
            LOGGER.i("更新完成 取消监听");
        } else {
            LOGGER.i("定位失败error=" + error + "|reseaon=" + s);
        }
    }

    @Override
    public void onStatusUpdate(String s, int i, String s1) {

    }
}
