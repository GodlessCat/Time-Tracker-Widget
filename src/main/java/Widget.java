import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Widget extends JButton implements CustomStatusBarWidget, AWTEventListener {

    private States states = new States();

    private boolean running = false;
    private long startAtMsec = 0;

    private boolean idle = false;
    private long lastActivityAtMsec = System.currentTimeMillis();

    private ScheduledFuture<?> ticker;

    Widget() {
        addActionListener(e -> setRunning(!running));
        setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
        setOpaque(false);
        setFocusable(false);
    }

    void setStates(States states) {
        this.states = states;
    }

    synchronized States getStates() {
        final long runningForSeconds = runningForSeconds();
        if (runningForSeconds > 0) {
            states.totalTimeSec += runningForSeconds;
            startAtMsec += runningForSeconds * 1000;
        }
        return states;
    }

    private long runningForSeconds() {
        if (!running) {
            return 0;
        } else {
            return Math.max(System.currentTimeMillis() - startAtMsec, 0) / 1000;
        }
    }

    private synchronized void setRunning(boolean running) {
        if (!this.running && running) {
            this.running = true;
            this.startAtMsec = System.currentTimeMillis();

            if (ticker != null) {
                ticker.cancel(false);
            }
            ticker = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(() -> UIUtil.invokeLaterIfNeeded(() -> {
                final long now = System.currentTimeMillis();
                if (now - lastActivityAtMsec > states.idleThresholdMsec) {
                    if (this.running) {
                        setRunning(false);
                        idle = true;
                    }
                }
                repaint();
            }), 1, 1, TimeUnit.SECONDS);
        } else if(this.running && !running) {
            states.totalTimeSec += runningForSeconds();
            this.running = false;

            if (ticker != null) {
                ticker.cancel(false);
                ticker = null;
            }
        }
    }

    @NotNull
    @Override
    public String ID() {
        return "TimeTracker";
    }

    @Nullable
    @Override
    public WidgetPresentation getPresentation(@NotNull PlatformType platformType) {
        return null;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        Toolkit.getDefaultToolkit().addAWTEventListener(this,
                AWTEvent.KEY_EVENT_MASK |
                        AWTEvent.MOUSE_EVENT_MASK |
                        AWTEvent.MOUSE_MOTION_EVENT_MASK
        );
    }

    @Override
    public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
        setRunning(false);
    }

    private static final Color COLOR_OFF = new JBColor(new Color(189, 188, 0), new Color(128, 126, 0));
    private static final Color COLOR_ON = new JBColor(new Color(0, 29, 200), new Color(3, 0, 113));
    private static final Color COLOR_IDLE = new JBColor(new Color(42, 164, 45), new Color(44, 163, 50));

    @Override
    public void paintComponent(final Graphics g) {
        long result;
        synchronized (this) {
            result = states.totalTimeSec + runningForSeconds();
        }
        final String info = formatDuration(result);

        final Dimension size = getSize();
        final Insets insets = getInsets();

        final int totalBarLength = size.width - insets.left - insets.right;
        final int barHeight = Math.max(size.height, getFont().getSize() + 2);
        final int yOffset = (size.height - barHeight) / 2;
        final int xOffset = insets.left;

        g.setColor(running ? COLOR_ON : (idle ? COLOR_IDLE : COLOR_OFF));
        g.fillRect(insets.left, insets.bottom, totalBarLength, size.height - insets.bottom - insets.top);

        final Color fg = getModel().isPressed() ? UIUtil.getLabelDisabledForeground() : JBColor.foreground();
        g.setColor(fg);
        UISettings.setupAntialiasing(g);
        g.setFont(getWidgetFont());
        final FontMetrics fontMetrics = g.getFontMetrics();
        final int infoWidth = fontMetrics.charsWidth(info.toCharArray(), 0, info.length());
        final int infoHeight = fontMetrics.getAscent();
        g.drawString(info, xOffset + (totalBarLength - infoWidth) / 2, yOffset + infoHeight + (barHeight - infoHeight) / 2 - 1);
    }

    private static String formatDuration(long secondDuration) {
        final Duration duration = Duration.ofSeconds(secondDuration);
        final StringBuilder sb = new StringBuilder();

        boolean found = false;
        final long days = duration.toDays();
        if(days != 0) {
            found = true;
            sb.append(days).append(" day");
            if (days != 1) {
                sb.append("s");
            }
        }
        final long hours = duration.toHours() % 24;
        if(found || hours != 0) {
            if(found) {
                sb.append(" ");
            }
            found = true;
            sb.append(hours).append(" hour");
            if (hours != 1) {
                sb.append("s");
            }
        }
        final long minutes = duration.toMinutes() % 60;
        if(found || minutes != 0) {
            if(found) {
                sb.append(" ");
            }
            found = true;
            sb.append(minutes).append(" min");/*
            if (minutes != 1) {
                sb.append("s");
            }*/
        }
        final long seconds = duration.getSeconds() % 60;
        {
            if(found) {
                sb.append(" ");
            }
            sb.append(seconds).append(" sec");/*
            if (seconds != 1) {
                sb.append("s");
            }*/
        }
        return sb.toString();
    }

    private static Font getWidgetFont() {
        return JBUI.Fonts.label(11);
    }

    private static final String SAMPLE_STRING = formatDuration(999999999999L);
    @Override
    public Dimension getPreferredSize() {
        final Insets insets = getInsets();
        int width = getFontMetrics(getWidgetFont()).stringWidth(SAMPLE_STRING) + insets.left + insets.right + JBUI.scale(2);
        int height = getFontMetrics(getWidgetFont()).getHeight() + insets.top + insets.bottom + JBUI.scale(2);
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        lastActivityAtMsec = System.currentTimeMillis();
        if (idle) {
            idle = false;
            setRunning(true);
        }
    }

    @Override
    public JComponent getComponent() {
        return this;
    }
}
