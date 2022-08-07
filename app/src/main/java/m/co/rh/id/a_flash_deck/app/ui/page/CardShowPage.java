/*
 *     Copyright (C) 2021 Ruby Hartono
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package m.co.rh.id.a_flash_deck.app.ui.page;

import android.app.Activity;
import android.net.Uri;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.text.HtmlCompat;

import java.io.File;
import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.ui.component.card.CardItemSV;
import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavActivityLifecycle;
import m.co.rh.id.aprovider.Provider;

public class CardShowPage extends StatefulView<Activity> implements NavActivityLifecycle, View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = CardShowPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;
    private transient Provider mSvProvider;
    private transient BehaviorSubject<Card> mCardStateSubject;
    private Card mCard;

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mCard = args.mCard;
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = BaseApplication.of(activity).getProvider().get(IStatefulViewProvider.class);
        setCardSubject(mCard);
        ViewGroup rootLayout = (ViewGroup)
                activity.getLayoutInflater().inflate(
                        R.layout.page_card_show, container, false);
        ImageView questionImageView = rootLayout.findViewById(R.id.image_question);
        questionImageView.setOnClickListener(this);
        ImageView answerImageView = rootLayout.findViewById(R.id.image_answer);
        answerImageView.setOnClickListener(this);
        Button buttonExit = rootLayout.findViewById(R.id.button_exit);
        buttonExit.setOnClickListener(this);
        Button buttonMenu = rootLayout.findViewById(R.id.button_menu);
        buttonMenu.setOnClickListener(this);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        buttonEdit.setOnClickListener(this);
        Button questionVoiceButton = rootLayout.findViewById(R.id.button_question_voice);
        questionVoiceButton.setOnClickListener(this);
        TextView textQuestion = rootLayout.findViewById(R.id.text_question);
        TextView textAnswer = rootLayout.findViewById(R.id.text_answer);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onCardShow",
                        mCardStateSubject.subscribe(
                                card -> {
                                    textQuestion.setText(HtmlCompat.fromHtml(card.question,
                                            HtmlCompat.FROM_HTML_MODE_LEGACY));
                                    textQuestion.setMovementMethod(LinkMovementMethod.getInstance());
                                    textAnswer.setText(HtmlCompat.fromHtml(card.answer,
                                            HtmlCompat.FROM_HTML_MODE_LEGACY));
                                    textAnswer.setMovementMethod(LinkMovementMethod.getInstance());
                                    if (card.questionImage != null) {
                                        File file = mSvProvider.get(FileHelper.class).getCardQuestionImage(card.questionImage);
                                        questionImageView.setImageURI(Uri.fromFile(file));
                                        questionImageView.setVisibility(View.VISIBLE);
                                    } else {
                                        questionImageView.setImageURI(null);
                                        questionImageView.setVisibility(View.GONE);
                                    }
                                    if (card.answerImage != null) {
                                        File file = mSvProvider.get(FileHelper.class).getCardAnswerImage(card.answerImage);
                                        answerImageView.setImageURI(Uri.fromFile(file));
                                        answerImageView.setVisibility(View.VISIBLE);
                                    } else {
                                        answerImageView.setImageURI(null);
                                        answerImageView.setVisibility(View.GONE);
                                    }
                                    if (card.questionVoice != null) {
                                        questionVoiceButton.setVisibility(View.VISIBLE);
                                    } else {
                                        questionVoiceButton.setVisibility(View.GONE);
                                    }
                                }
                        )
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onCardChanged",
                        mSvProvider.get(DeckChangeNotifier.class).getUpdatedCardFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(card -> {
                                    mCard = card;
                                    setCardSubject(card);
                                }));
        return rootLayout;
    }

    private void setCardSubject(Card card) {
        if (mCardStateSubject == null) {
            if (card == null) {
                mCardStateSubject = BehaviorSubject.create();
            } else {
                mCardStateSubject = BehaviorSubject.createDefault(card);
            }
        } else {
            mCardStateSubject.onNext(card);
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mCardStateSubject != null) {
            mCardStateSubject.onComplete();
            mCardStateSubject = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_exit) {
            mNavigator.finishActivity();
        } else if (id == R.id.button_menu) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.page_card_show, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();//showing popup menu
        } else if (id == R.id.button_edit) {
            mNavigator.push(Routes.CARD_DETAIL_PAGE,
                    CardDetailPage.Args.forUpdate(mCard), (navigator, navRoute, activity, currentView) ->
                            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        } else if (id == R.id.image_question) {
            CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
            FileHelper fileHelper = mSvProvider.get(FileHelper.class);
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    commonNavConfig.args_commonImageView(
                            fileHelper.getCardQuestionImage(mCard.questionImage)));
        } else if (id == R.id.image_answer) {
            CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
            FileHelper fileHelper = mSvProvider.get(FileHelper.class);
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    commonNavConfig.args_commonImageView(
                            fileHelper.getCardAnswerImage(mCard.answerImage)));
        } else if (id == R.id.button_question_voice) {
            FileHelper fileHelper = mSvProvider.get(FileHelper.class);
            File file = fileHelper.getCardQuestionVoice(mCard.questionVoice);
            mSvProvider.get(AudioPlayer.class).play(Uri.fromFile(file));
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_move_card) {
            CardItemSV.moveCardAction(mNavigator, mCard.clone());
            return true;
        } else if (id == R.id.menu_copy_card) {
            CardItemSV.copyCardAction(mNavigator, mCard.clone());
            return true;
        }
        return false;
    }

    @Override
    public void onNavActivityResumed(Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onNavActivityPaused(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public static class Args implements Serializable {
        public static Args withCard(Card card) {
            Args args = new Args();
            args.mCard = card;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private Card mCard;

        private Args() {
        }

        public Card getCard() {
            return mCard;
        }
    }
}
