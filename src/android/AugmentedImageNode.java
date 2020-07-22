package com.gj.arcoredraw;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;

import com.google.ar.core.AugmentedImage;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.Color;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Node for rendering an augmented image. The image is framed by placing the virtual picture frame
 * at the corners of the augmented image trackable.
 */
@SuppressWarnings({"AndroidApiChecker"})
public class AugmentedImageNode extends AnchorNode {

    private static final String TAG = "ARPlugin: it.linup " + AugmentedImageNode.class.getSimpleName();


    // The augmented image represented by this node.
  private AugmentedImage image;

  private boolean isInfoNode = false;

  private OnClickedListener onClickFunction;

  // Models of the 4 corners.  We use completable futures here to simplify
  // the error handling and asynchronous loading.  The loading is started with the
  // first construction of an instance, and then used when the image is set.
  private static CompletableFuture<ModelRenderable> ulCorner;
  private static CompletableFuture<ModelRenderable> urCorner;
  private static CompletableFuture<ModelRenderable> lrCorner;
  private static CompletableFuture<ModelRenderable> llCorner;

  public AugmentedImageNode(Context context) {
    // Upon construction, start loading the models for the corners of the frame.
    if (ulCorner == null) {
      ulCorner =
          ModelRenderable.builder()
              .setSource(context, Uri.parse("models/frame_upper_left.sfb"))
              .build();
      urCorner =
          ModelRenderable.builder()
              .setSource(context, Uri.parse("models/frame_upper_right.sfb"))
              .build();
      llCorner =
          ModelRenderable.builder()
              .setSource(context, Uri.parse("models/frame_lower_left.sfb"))
              .build();
      lrCorner =
          ModelRenderable.builder()
              .setSource(context, Uri.parse("models/frame_lower_right.sfb"))
              .build();
    }
  }

  /**
   * Called when the AugmentedImage is detected and should be rendered. A Sceneform node tree is
   * created based on an Anchor created from the image. The corners are then positioned based on the
   * extents of the image. There is no need to worry about world coordinates since everything is
   * relative to the center of the image, which is the parent node of the corners.
   */
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})

    public void setImage(AugmentedImage image, Context ctx) {
      isInfoNode = false;
      //Log.d(TAG, "setImage");
      this.image = image;
      // Set the anchor based on the center of the image.
      //Log.d(TAG, "setAnchor");
      setAnchor(image.createAnchor(image.getCenterPose()));

      // Make the 4 corner nodes.
      //Log.d(TAG, "localPosition");
      Vector3 localPosition = new Vector3();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          //Log.d(TAG, "make opaque");
          MaterialFactory.makeOpaqueWithColor(ctx, new Color(0.33f, 0.87f, 0f))
                  .thenAccept(material -> {
                      //Log.d(TAG, "then...");
                      ModelRenderable sphere = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material);
                      //Log.d(TAG, "sphere created");
                      Node sphereNode = new Node();
                      //Log.d(TAG, "sphere node created");
                      sphereNode.setParent(this);
                      //Log.d(TAG, "parent set");
                      sphereNode.setLocalPosition(Vector3.zero());
                      //Log.d(TAG, "set local position");
                      sphereNode.setRenderable(sphere);
                      //Log.d(TAG, "set renderable");
                  });
      } else {
          Log.d(TAG, "is not N???");
      }
  }

    public void setImage(AugmentedImage image) {

        this.image = image;

    // If any of the models are not loaded, then recurse when all are loaded.
    if (!ulCorner.isDone() || !urCorner.isDone() || !llCorner.isDone() || !lrCorner.isDone()) {
      CompletableFuture.allOf(ulCorner, urCorner, llCorner, lrCorner)
          .thenAccept((Void aVoid) -> setImage(image))
          .exceptionally(
              throwable -> {
                Log.e(TAG, "Exception loading", throwable);
                return null;
              });
    }

    // Set the anchor based on the center of the image.
    setAnchor(image.createAnchor(image.getCenterPose()));

    // Make the 4 corner nodes.
    Vector3 localPosition = new Vector3();
    Node cornerNode;

    // Upper left corner.
    localPosition.set(-0.5f * image.getExtentX(), 0.0f, -0.5f * image.getExtentZ());
    cornerNode = new Node();
    cornerNode.setParent(this);
    cornerNode.setLocalPosition(localPosition);
    cornerNode.setRenderable(ulCorner.getNow(null));

    // Upper right corner.
    localPosition.set(0.5f * image.getExtentX(), 0.0f, -0.5f * image.getExtentZ());
    cornerNode = new Node();
    cornerNode.setParent(this);
    cornerNode.setLocalPosition(localPosition);
    cornerNode.setRenderable(urCorner.getNow(null));

    // Lower right corner.
    localPosition.set(0.5f * image.getExtentX(), 0.0f, 0.5f * image.getExtentZ());
    cornerNode = new Node();
    cornerNode.setParent(this);
    cornerNode.setLocalPosition(localPosition);
    cornerNode.setRenderable(lrCorner.getNow(null));

    // Lower left corner.
    localPosition.set(-0.5f * image.getExtentX(), 0.0f, 0.5f * image.getExtentZ());
    cornerNode = new Node();
    cornerNode.setParent(this);
    cornerNode.setLocalPosition(localPosition);
    cornerNode.setRenderable(llCorner.getNow(null));



   /* MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(0.33f, 0.87f, 0f))
                        .thenAccept { material ->
            val sphere = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material)
      sphereNodeArray.add(    Node().apply {
        setParent(startNode)
        localPosition = Vector3.zero()
        renderable = sphere
      })
    }*/
  }

  public AugmentedImage getImage() {
    return image;
  }

    public void setClickedImage(AugmentedImage image, Context ctx, AugmentedImageNode.OnClickedListener var1) {
        this.isInfoNode = true;
        this.onClickFunction = var1;
        //Log.d(TAG, "setImage");
        this.image = image;
        // Set the anchor based on the center of the image.
        //Log.d(TAG, "setAnchor");
        setAnchor(image.createAnchor(image.getCenterPose()));

        // Make the 4 corner nodes.
        //Log.d(TAG, "localPosition");
        Vector3 localPosition = new Vector3();
        localPosition.set(0.0f, 0.0f, 0.5f * image.getExtentZ());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //Log.d(TAG, "make opaque");
            MaterialFactory.makeOpaqueWithColor(ctx, new Color(0.87f,0f, 0.33f))
                    .thenAccept(material -> {
                        //Log.d(TAG, "then...");
                        Vector3 size = new Vector3(0.04f, 0.02f, 0.02f);
                        //ModelRenderable sphere = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material);
                        ModelRenderable block = ShapeFactory.makeCube(size, Vector3.zero(), material);
                        //Log.d(TAG, "sphere created");
                        Node sphereNode = new Node();
                        //Log.d(TAG, "sphere node created");
                        sphereNode.setParent(this);
                        //Log.d(TAG, "parent set");
                        sphereNode.setLocalPosition(localPosition);
                        //Log.d(TAG, "set local position");
                        sphereNode.setRenderable(block);
                        //Log.d(TAG, "set renderable");
                    });
        } else {
            Log.d(TAG, "is not N???");
        }
    }

    @Override
    public boolean onTouchEvent(HitTestResult hitTestResult, MotionEvent motionEvent) {
        //return super.onTouchEvent(hitTestResult, motionEvent);
        Log.d(TAG, "onTouchEvent");
        if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
            Log.d(TAG, "touched up!!!");
            if(this.onClickFunction != null) {
                this.onClickFunction.onClick(this);
            }
        }
        return super.onTouchEvent(hitTestResult, motionEvent);
    }

    public boolean getIsInfoNode() {
      return this.isInfoNode;
    }

    public interface OnClickedListener {
        void onClick(AugmentedImageNode var1);
    }
}

