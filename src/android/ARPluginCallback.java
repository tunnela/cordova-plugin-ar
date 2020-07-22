package com.gj.arcoredraw;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

public class ARPluginCallback {

    public static CallbackContext setMeasureListenerCallbackContext = null;
    public static CallbackContext setFinishListenerCallbackContext = null;
    public static CallbackContext setClickedAugmentedImage = null;

    public static void onUpdate(String value) {
        if (setFinishListenerCallbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, value);
            pluginResult.setKeepCallback(true);
            setMeasureListenerCallbackContext.sendPluginResult(pluginResult);
        }
    }

    public static void onFinish(List<String> data) {
        if (setFinishListenerCallbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, new JSONArray(data));
            pluginResult.setKeepCallback(true);
            setFinishListenerCallbackContext.sendPluginResult(pluginResult);
        }
    }

    public static void onClick(String value) {
        if (setClickedAugmentedImage != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, value);
            pluginResult.setKeepCallback(true);
            setClickedAugmentedImage.sendPluginResult(pluginResult);
        }
    }


}