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
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateCardCmd;
import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class CardDetailPage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener {
    private static final String TAG = CardDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private transient NavRoute mNavRoute;
    private Card mCard;
    private transient Provider mSvProvider;
    private transient NewCardCmd mNewCardCmd;
    private transient TextWatcher mQuestionTextWatcher;
    private transient TextWatcher mAnswerTextWatcher;

    public CardDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_card_detail);
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mCard = new Card();
        mCard.question = "";
        mCard.answer = "";
        Args args = getArgs();
        if (args != null) {
            if (args.isUpdate()) {
                mCard = args.mCard;
            } else {
                mCard.deckId = args.mDeck.id;
            }
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = BaseApplication.of(activity).getProvider().get(IStatefulViewProvider.class);
        initTextWatcher();
        ViewGroup rootLayout = (ViewGroup)
                activity.getLayoutInflater().inflate(
                        R.layout.page_card_detail, container, false);
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_card));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_new_card));
        }
        mAppBarSV.setMenuItemClick(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        EditText editTextQuestion = rootLayout.findViewById(R.id.text_input_edit_question);
        EditText editTextAnswer = rootLayout.findViewById(R.id.text_input_edit_answer);
        if (mCard != null) {
            editTextQuestion.setText(mCard.question);
            editTextAnswer.setText(mCard.answer);
        }
        editTextQuestion.addTextChangedListener(mQuestionTextWatcher);
        editTextAnswer.addTextChangedListener(mAnswerTextWatcher);
        if (isUpdate()) {
            mNewCardCmd = mSvProvider.get(UpdateCardCmd.class);
        } else {
            mNewCardCmd = mSvProvider.get(NewCardCmd.class);
        }
        mSvProvider.get(RxDisposer.class)
                .add("createView_questionValid",
                        mNewCardCmd
                                .getQuestionValid().subscribe(s -> {
                            if (!s.isEmpty()) {
                                editTextQuestion.setError(s);
                            } else {
                                editTextQuestion.setError(null);
                            }
                        }));
        mSvProvider.get(RxDisposer.class)
                .add("createView_answerValid",
                        mNewCardCmd
                                .getAnswerValid().subscribe(s -> {
                            if (!s.isEmpty()) {
                                editTextAnswer.setError(s);
                            } else {
                                editTextAnswer.setError(null);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        mNavRoute = null;
        mCard = null;
        mNewCardCmd = null;
        mQuestionTextWatcher = null;
        mAnswerTextWatcher = null;
        mNavigator = null;
    }

    private Args getArgs() {
        return Args.of(mNavRoute);
    }

    private boolean isUpdate() {
        Args args = getArgs();
        return args != null && args.isUpdate();
    }

    private void initTextWatcher() {
        if (mQuestionTextWatcher == null) {
            mQuestionTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mCard.question = editable.toString();
                    mSvProvider.get(NewCardCmd.class)
                            .valid(mCard);
                }
            };
        }
        if (mAnswerTextWatcher == null) {
            mAnswerTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mCard.answer = editable.toString();
                    mSvProvider.get(NewCardCmd.class)
                            .valid(mCard);
                }
            };
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
            if (mNewCardCmd.valid(mCard)) {
                Context context = mSvProvider.getContext();
                String errorMessage;
                String successMessage;
                Args args = getArgs();
                if (args != null && args.isUpdate()) {
                    errorMessage = context.getString(R.string.error_failed_to_update_card);
                    successMessage = context.getString(R.string.success_updating_card);
                } else {
                    errorMessage = context.getString(R.string.error_failed_to_add_card);
                    successMessage = context.getString(R.string.success_adding_new_card);
                }
                mSvProvider.get(RxDisposer.class).add("newCard.save",
                        mNewCardCmd.execute(mCard)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((card, throwable) -> {
                                    ILogger iLogger = mSvProvider.get(ILogger.class);
                                    if (throwable != null) {
                                        iLogger.e(TAG,
                                                errorMessage);
                                    } else {
                                        iLogger.i(TAG,
                                                successMessage);
                                        mNavigator.pop(Result.withCard(card));
                                    }
                                }));
            } else {
                String validationError = mNewCardCmd.getValidationError();
                mSvProvider.get(ILogger.class).i(TAG, validationError);
            }
            return true;
        }
        return false;
    }

    public static class Result implements Serializable {
        public static Result withCard(Card card) {
            Result result = new Result();
            result.mCard = card;
            return result;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private Card mCard;

        public Card getCard() {
            return mCard;
        }
    }

    /**
     * Argument for this SV
     */
    public static class Args implements Serializable {
        public static Args withDeck(Deck deck) {
            Args args = new Args();
            args.mDeck = deck;
            return args;
        }

        public static Args forUpdate(Card card) {
            Args args = new Args();
            args.mCard = card;
            args.mOperation = 1;
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

        private Deck mDeck;
        private Card mCard;
        private byte mOperation;

        private Args() {
        }

        public Deck getDeck() {
            return mDeck;
        }

        public boolean isUpdate() {
            return mOperation == 1;
        }
    }
}
