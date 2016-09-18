package org.toilelibre.libe.singin;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.toilelibre.libe.singin.poc.Sound2GraphSeries;
import org.toilelibre.libe.singin.scenes.Transitions;
import org.toilelibre.libe.singin.transition.BlinkAnimation;
import org.toilelibre.libe.soundtransform.actions.fluent.FluentClient;
import org.toilelibre.libe.soundtransform.model.converted.sound.Sound;
import org.toilelibre.libe.soundtransform.model.exception.SoundTransformException;
import org.toilelibre.libe.soundtransform.model.inputstream.StreamInfo;
import org.toilelibre.libe.soundtransform.model.record.AmplitudeObserver;

import com.github.glomadrian.velocimeterlibrary.VelocimeterView;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.skyfishjy.library.RippleBackground;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.CardView;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;
import io.codetail.widget.RevealFrameLayout;

public class AppSingleActivity extends Activity {

    @Bind (R.id.btn_record_cancel_button)
    @Nullable
    FloatingActionButton cancelRecord;
    @Bind (R.id.btn_record_sound)
    @Nullable
    FloatingActionButton recordASound;
    @Bind (R.id.btn_open_project)
    @Nullable
    FloatingActionButton openAProject;
    @Bind (R.id.earAnim)
    @Nullable
    ImageView            earAnimPicture;
    @Bind (R.id.rippleEar)
    @Nullable
    RippleBackground     earAnim;
    @Bind (R.id.ready_textview)
    @Nullable
    TextView             readyText;
    @Bind (R.id.countdown_textview)
    @Nullable
    ShimmerTextView      countdownText;
    @Bind (R.id.velocimeter)
    @Nullable
    VelocimeterView      velocimeterView;
    @Bind (R.id.btn_validate_record_button)
    @Nullable
    FloatingActionButton validateSoundRecord;
    @Bind (R.id.reveal_record_frame_layout)
    @Nullable
    RevealFrameLayout    revealFrameLayout;
    @Bind (R.id.edition_screen)
    @Nullable
    LinearLayout         editionScreenLayout;
    @Bind (R.id.edition_screen_text_view)
    @Nullable
    TextView             endOfRecordingTextView;
    @Bind (R.id.timer_textview)
    @Nullable
    TextView             timerTextView;
    @Bind (R.id.channels)
    @Nullable
    LinearLayout         editorChannels;

    private final List<Sound> sounds = new LinkedList<Sound>();
    private final Object stopRecording = new Object ();

    private Timer            recordTimer = null;
    private Timer            countdownTimer = null;
    private Handler          handler   = null;
    private Shimmer          shimmer;
    private long             recordStartTimestamp;
    private static final int COUNTDOWN = 5;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        this.setContentView (R.layout.welcome_screen);
        this.welcome();
    }

    private void welcome() {
        Transitions.welcomeScene (this);
        ButterKnife.bind (this);
        assert this.recordASound != null;
        this.recordASound.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View v) {AppSingleActivity.this.recordSound();
            }

        });
        assert this.openAProject != null;
        this.openAProject.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View v) {
                openProject();
            }

        });
    }

    private void openProject() {
    }

    private void recordSound() {
        Transitions.recordScene (this);
        ButterKnife.bind (this);
        landOnRecordSceneAnimation ();
        assert this.cancelRecord != null;
        this.cancelRecord.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View v) {
                cancelRecordAnimation ();
                AppSingleActivity.this.welcome();
            }

        });
        assert this.validateSoundRecord != null;
        this.validateSoundRecord.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View v) {
                endOfRecordingAnimationAndAfterRun(new Runnable() {@Override public void run() {displayEditor ();}});
            }
        });
    }

    private void displayEditor() {
        Transitions.editorScene (this);
        ButterKnife.bind (this);
        assert this.editorChannels != null;
        final Sound2GraphSeries converter = new Sound2GraphSeries();
        for (Sound sound : this.sounds) {
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            CardView cardView = (CardView) this.getLayoutInflater().inflate(R.layout.editor_channel, editorChannels).findViewById(R.id.card_view);
            cardView.animate();
            GraphView graphView = (GraphView)cardView.findViewById(R.id.graph2);
            graphView.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
            graphView.getGridLabelRenderer().setHorizontalLabelsVisible(false);
            graphView.getGridLabelRenderer().setVerticalLabelsVisible(false);
            graphView.addSeries(converter.convert(sound.getChannels()[0], Math.max(size.x, size.y)));
        }
    }

    private void startTimerForSoundRecording () {
        this.cancelTimer ();
        this.countdownTimer = new Timer ();
        this.recordTimer = new Timer ();
        this.handler = new Handler ();
        this.shimmer = new Shimmer ();
        assert this.timerTextView != null;
        this.timerTextView.setText (this.getText (R.string.zerozero));
        shimmer.start (this.countdownText);
        this.countdownTimer.scheduleAtFixedRate (new TimerTask () {
            int occurence = AppSingleActivity.COUNTDOWN;

            @Override
            public void run () {
                if (occurence == 0) {
                    new Thread () {
                        public void run () {
                            AppSingleActivity.this.startRecording ();
                        }
                    }.start ();
                    this.cancel ();
                    AppSingleActivity.this.handler.post (new Runnable () {
                        public void run () {
                            AppSingleActivity.this.startRecordingAnimation ();
                        }
                    });
                    this.cancel ();
                } else {
                    occurence--;
                    AppSingleActivity.this.handler.post (new Runnable () {
                        public void run () {
                            assert AppSingleActivity.this.countdownText != null;
                            AppSingleActivity.this.countdownText.setText ("" + (occurence + 1));
                        }
                    });
                }
            }

        }, 0, 1000);
    }

    private void cancelTimer () {
        if (this.countdownTimer != null) {
            this.countdownTimer.cancel ();
            this.recordTimer.cancel ();
            this.shimmer.cancel ();
            this.countdownTimer = null;
            this.shimmer = null;
        }

    }

    private void startRecording () {
        try {
            this.sounds.add(FluentClient.start ().whileRecordingASound (new StreamInfo (1, -1, 2, 8000, false, true, null), this.stopRecording, new AmplitudeObserver () {
                @Override
                public void update (final float soundLevel) {
                    AppSingleActivity.this.handler.post (new Runnable () {
                        public void run () {
                            VelocimeterView view = AppSingleActivity.this.velocimeterView;
                            if (view != null) {
                                AppSingleActivity.this.velocimeterView.setValue (soundLevel, false);
                            }
                        }
                    });
                }
            }).stopWithSound ());
            this.recordStartTimestamp = System.currentTimeMillis () - 1000;
            this.countdownTimer.scheduleAtFixedRate (new TimerTask () {
                @Override
                public void run () {
                    int secs = (int) Math.round ((System.currentTimeMillis () - AppSingleActivity.this.recordStartTimestamp * 1.0) / 1000);
                    final String minutes = String.format (Locale.getDefault (), "%02d", secs / 60);
                    final String seconds = String.format (Locale.getDefault (), "%02d", secs % 60);
                    AppSingleActivity.this.handler.post (new Runnable () {
                        public void run () {
                            assert AppSingleActivity.this.timerTextView != null;
                            AppSingleActivity.this.timerTextView.setText (minutes + ":" + seconds);
                        }
                    });
                }
            }, 0, 1000);
        } catch (SoundTransformException e) {
            synchronized (this.stopRecording) {
                this.stopRecording.notifyAll ();
            }
            if ("STREAM_INFO_NOT_SUPPORTED".equals(e.getErrorCode().name())) {

            }
            throw new RuntimeException (e);
        }
    }

    private void endOfRecordAction () {
        this.cancelTimer ();
        synchronized (this.stopRecording) {
            this.stopRecording.notifyAll ();
        }
    }

    private void startRecordingAnimation () {
        assert this.readyText != null;
        assert this.countdownText != null;
        assert this.validateSoundRecord != null;
        assert this.velocimeterView != null;
        assert this.timerTextView != null;
        assert this.earAnim != null;
        this.readyText.setText (R.string.sing_now);
        this.countdownText.setText ("");
        this.validateSoundRecord.setVisibility (View.VISIBLE);
        this.velocimeterView.setVisibility (View.VISIBLE);
        this.timerTextView.setVisibility (View.VISIBLE);
        this.velocimeterView.setProgress (new DecelerateInterpolator (10));
        this.earAnim.startRippleAnimation ();
    }

    private void endOfRecordingAnimationAndAfterRun(final Runnable afterAnimation) {
        this.endOfRecordAction ();
        assert this.validateSoundRecord != null;
        assert this.cancelRecord != null;
        assert endOfRecordingTextView != null;
        assert editionScreenLayout != null;
        this.validateSoundRecord.setVisibility (View.VISIBLE);
        this.cancelRecord.setVisibility (View.INVISIBLE);
        endOfRecordingTextView.setText (R.string.impressive);
        editionScreenLayout.setVisibility (View.VISIBLE);
        int sizeOfTheButton = validateSoundRecord.getWidth ();
        int screenDiagonal = (int) Math.sqrt (Math.pow (editionScreenLayout.getWidth (), 2) + Math.pow (editionScreenLayout.getHeight (), 2));
        SupportAnimator animator = ViewAnimationUtils.createCircularReveal (editionScreenLayout, editionScreenLayout.getWidth () - sizeOfTheButton / 2,
                editionScreenLayout.getHeight () - sizeOfTheButton / 2, sizeOfTheButton / 2, screenDiagonal);
        animator.setInterpolator (new AccelerateDecelerateInterpolator ());
        animator.setDuration (750);
        animator.start ();
        BlinkAnimation blinkAnimation = new BlinkAnimation();
        blinkAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {} @Override public void onAnimationRepeat(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                afterAnimation.run();
            }
        });
        this.endOfRecordingTextView.startAnimation(blinkAnimation);
    }

    private void landOnRecordSceneAnimation () {
        this.startTimerForSoundRecording ();
        assert this.earAnimPicture != null;
        assert this.editionScreenLayout != null;
        assert this.cancelRecord != null;
        assert this.validateSoundRecord != null;
        assert this.velocimeterView != null;
        assert this.timerTextView != null;
        assert this.readyText != null;
        this.earAnimPicture.setVisibility (View.VISIBLE);
        this.cancelRecord.setVisibility (View.VISIBLE);
        this.validateSoundRecord.setVisibility (View.INVISIBLE);
        this.velocimeterView.setVisibility (View.INVISIBLE);
        this.timerTextView.setVisibility (View.INVISIBLE);
        this.editionScreenLayout.setVisibility (View.INVISIBLE);
        this.readyText.setText (R.string.ready);
    }

    private void cancelRecordAnimation () {
        assert this.earAnimPicture != null;
        assert this.cancelRecord != null;
        assert this.validateSoundRecord != null;
        assert this.velocimeterView != null;
        assert this.timerTextView != null;
        assert this.endOfRecordingTextView != null;
        assert this.countdownText != null;
        assert this.earAnim != null;
        this.validateSoundRecord.setVisibility (View.INVISIBLE);
        this.velocimeterView.setVisibility (View.INVISIBLE);
        this.endOfRecordingTextView.setText (R.string.nevermind);
        this.countdownText.setVisibility (View.INVISIBLE);
        this.earAnimPicture.setVisibility (View.INVISIBLE);
        this.timerTextView.setVisibility (View.INVISIBLE);
        this.endOfRecordAction ();
        this.earAnim.stopRippleAnimation ();
    }
}
