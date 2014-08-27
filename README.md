Android Weak Handler
====================

Memory safer implementation of android.os.Handler

Problem
-------

Original implementation of Handler always keeps hard reference to handler in queue of execution.
Any object in Message or Runnable posted to `android.os.Handler` will be hard referenced for some time. 
If you create anonymous Runnable and call to `postDelayed` with large timeout, that Runnable will be held
in memory until timeout passes. Even if your Runnable seems small, it indirectly references owner class, 
which is usually something as big as Activity or Fragment.
 
Very soon we will post article with much more details.

Solution
--------

`WeakHandler` is trickier then `android.os.Handler` , it will keep `WeakReferences` to runnables and messages,
and GC could collect them once `WeakHandler` instance is not referenced any more.

![Screenshot](WeakHandler.png)

Usage
-----
Clone project to your local machine, then run
```shell
cd android-weak-handler
gradle uploadArchives
```

Add reference to your build.gradle:
```groovy
repositories {
    mavenLocal()
}

dependencies {
    compile 'com.badoo.mobile:android-weak-handler:1.0'
}
```

Use WeakHandler as you normally would use Handler

```java
import com.badoo.mobile.util.WeakHandler;

public class ExampleActivity extends Activity {

    private WeakHandler mHandler; // We still need at least one hard reference to WeakHandler
    
    protected void onCreate(Bundle savedInstanceState) {
        mHandler = new WeakHandler();
        ...
    }
    
    private void onClick(View view) {
        mHandler.postDelayed(new Runnable() {
            view.setVisibility(View.INVISIBLE);
        }, 5000);
    }
}
```

Credits
-------
Weak Handler is brought to you by [Badoo Trading Limited](http://corp.badoo.com) and it is released under the [MIT License](http://opensource.org/licenses/MIT).

Created by [Dmytro Voronkevych](https://github.com/dmitry-voronkevich)
