# cordova-plugin-ar

## Supported Platforms

- iOS

## Installation

    cordova plugin add https://github.com/eclettica/cordova-plugin-ar.git

Add the following lines into your config.xml in the platform tag.
```xml
<platform name="ios">
  <preference name="UseSwiftLanguageVersion" value="4.2" />
</platform>
```

## Methods

- cordova.plugins.ar.addARView
- cordova.plugins.ar.removeARView
- cordova.plugins.ar.setListenerForArChanges
- cordova.plugins.ar.reloadSession

### addARView

Insert the camera view under the WebView

```js
cordova.plugins.ar.addARView();
```

### removeARView

Remove the camera view

```js
cordova.plugins.ar.removeARView();
```

### setListenerForArChanges

Set listener for event from ARKit

##### Parameters

| Parameter        | Type       | Description                                |
| ---------------- | ---------- | ------------------------------------------ |
| `arHandler`      | `Function` | Is called after initializing an AR session |

##### Callback parameters

`arHandler`

| Parameter  | Type      | Description                         |
| ---------- | --------- | ----------------------------------- |
|   `str`    | `String`  | Line with camera change information. <br> Format: `positionX, positionY, positionZ, quatirionX, quatirionY, quatirionZ, quatirionW` |


```js
cordova.plugins.ar.setListenerForArChanges((str) => {});
```

### reloadSession

Reload AR session

```js
cordova.plugins.ar.reloadSession();
```
