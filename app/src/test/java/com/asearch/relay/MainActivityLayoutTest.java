package com.asearch.relay;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(
        sdk = 35,
        application = Application.class,
        qualifiers = "port"
)
public class MainActivityLayoutTest {
    @Test
    public void portraitNavigationKeepsAllSixTabsVisibleAndTouchable() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create()
                .start()
                .resume();
        MainActivity activity = controller.get();
        ViewGroup content = activity.findViewById(android.R.id.content);
        List<Button> buttons = new ArrayList<>();
        collectButtons(content, buttons);

        List<String> expected = Arrays.asList(
                "TODAY", "OPPORTUNITIES", "CALENDAR",
                "FOLLOW-UPS", "CONTACTS", "ACTIVITY"
        );
        int found = 0;
        for (Button button : buttons) {
            if (!expected.contains(button.getText().toString())) continue;
            found++;
            assertTrue(button.getMinimumHeight() > 0);
            assertTrue(button.getLayoutParams().height > 0);
            assertTrue(button.isClickable());
        }
        assertEquals(6, found);
        controller.pause().stop().destroy();
    }

    private static void collectButtons(View view, List<Button> result) {
        if (view instanceof Button) result.add((Button) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            collectButtons(group.getChildAt(index), result);
        }
    }
}
