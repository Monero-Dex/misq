package io.bitsquare.gui.components;

import ch.qos.logback.classic.Logger;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class BusyAnimation extends Pane {
    private static final Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BusyAnimation.class);

    private Timer timer;
    private ImageView img1, img2;
    private int incr;
    private int rotation1, rotation2;
    private boolean animate;

    public BusyAnimation() {
        this(true);
    }

    public BusyAnimation(boolean animate) {
        this.animate = animate;
        setMinSize(24, 24);
        setMaxSize(24, 24);

        incr = 360 / 12;

        img1 = new ImageView();
        img1.setId("spinner");
        img2 = new ImageView();
        img2.setId("spinner");

        getChildren().addAll(img1, img2);

        sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null)
                stop();
            else if (animate)
                play();
        });

        updateVisibility();
    }

    public void play() {
        animate = true;
        updateVisibility();

        if (timer != null)
            timer.stop();
        timer = UserThread.runPeriodically(this::updateAnimation, 100, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        animate = false;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        updateVisibility();
    }

    private void updateAnimation() {
        rotation1 += incr;
        rotation2 -= incr;
        img1.setRotate(rotation1);
        img2.setRotate(rotation2);
    }

    private void updateVisibility() {
        setVisible(animate);
        setManaged(animate);
    }
}