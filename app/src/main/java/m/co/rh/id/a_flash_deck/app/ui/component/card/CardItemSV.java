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

package m.co.rh.id.a_flash_deck.app.ui.component.card;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.CopyCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.DeckQueryCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.DeleteCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.MoveCardCmd;
import m.co.rh.id.a_flash_deck.app.ui.page.CardDetailPage;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckSelectSVDialog;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.CopyCardEvent;
import m.co.rh.id.a_flash_deck.base.model.MoveCardEvent;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class CardItemSV extends StatefulView<Activity> implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = CardItemSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private Card mCard;
    private transient BehaviorSubject<Card> mCardSubject;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        initSubject();
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.item_card, container, false);
        rootLayout.setOnClickListener(this);
        ImageView imageQuestion = rootLayout.findViewById(R.id.image_question);
        imageQuestion.setOnClickListener(this);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        Button buttonDelete = rootLayout.findViewById(R.id.button_delete);
        Button buttonMore = rootLayout.findViewById(R.id.button_more_action);
        buttonEdit.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        buttonMore.setOnClickListener(this);
        TextView textQuestion = rootLayout.findViewById(R.id.text_question);
        TextView textAnswer = rootLayout.findViewById(R.id.text_answer);
        TextView textDeckName = rootLayout.findViewById(R.id.text_deck_name);
        mSvProvider.get(RxDisposer.class).add("createView_onChangeCard",
                mCardSubject.observeOn(AndroidSchedulers.mainThread())
                        .subscribe(card -> {
                            FileHelper fileHelper = mSvProvider.get(FileHelper.class);
                            Context context = mSvProvider.getContext();
                            textQuestion.setText(context.getString(R.string.question_desc_value, card.question));
                            textAnswer.setText(context.getString(R.string.answer_desc_value, card.answer));
                            if (card.questionImage != null) {
                                imageQuestion.setImageURI(Uri.fromFile(fileHelper.getCardQuestionImageThumbnail(card.questionImage)));
                                imageQuestion.setVisibility(View.VISIBLE);
                            } else {
                                imageQuestion.setImageURI(null);
                                imageQuestion.setVisibility(View.GONE);
                            }
                            mSvProvider.get(RxDisposer.class).add("createView_onChangeCard_getDeckById",
                                    mSvProvider.get(DeckQueryCmd.class).getDeckById(card.deckId)
                                            .subscribe((deck, throwable) -> {
                                                if (throwable != null) {
                                                    mSvProvider.get(ILogger.class).e(TAG, context.getString(R.string.error_loading_deck), throwable);
                                                } else {
                                                    textDeckName.setText(deck.name);
                                                }
                                            })
                            );
                        }));
        mSvProvider.get(RxDisposer.class).add("createView_onMoveCard",
                mSvProvider.get(DeckChangeNotifier.class)
                        .getMovedCardFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(moveCardEvent -> {
                            Context context = mSvProvider.getContext();
                            Card card = moveCardEvent.getMovedCard();
                            if (card.id.equals(mCard.id)) {
                                textQuestion.setText(context.getString(R.string.question_desc_value, card.question));
                                textAnswer.setText(context.getString(R.string.answer_desc_value, card.answer));
                                mSvProvider.get(RxDisposer.class).add("createView_onMoveCard_getDeckById",
                                        mSvProvider.get(DeckQueryCmd.class)
                                                .getDeckById(moveCardEvent.getDestinationDeck().id)
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe((deck, throwable) -> {
                                                    if (throwable != null) {
                                                        mSvProvider.get(ILogger.class).e(TAG, context.getString(R.string.error_loading_deck), throwable);
                                                    } else {
                                                        textDeckName.setText(deck.name);
                                                    }
                                                })
                                );
                            }
                        }));
        return rootLayout;
    }

    private void initSubject() {
        if (mCardSubject == null) {
            if (mCard != null) {
                mCardSubject = BehaviorSubject.createDefault(mCard);
            } else {
                mCardSubject = BehaviorSubject.create();
            }
        } else {
            mCardSubject.onNext(mCard);
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        if (mCardSubject != null) {
            mCardSubject.onComplete();
            mCardSubject = null;
        }
        mCard = null;
        mProvider = null;
        mNavigator = null;
    }

    public void setCard(Card card) {
        mCard = card;
        initSubject();
    }

    public Card getCard() {
        return mCard;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_edit) {
            mNavigator.push(Routes.CARD_DETAIL_PAGE, CardDetailPage.Args.forUpdate(mCard.clone()),
                    (navigator, navRoute, activity, currentView) -> {
                        CardDetailPage.Result result = CardDetailPage.Result.of(navRoute.getRouteResult());
                        if (result != null) {
                            setCard(result.getCard());
                        }
                    });
        } else if (id == R.id.button_delete) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_card, mCard.question);
            CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
            mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                    commonNavConfig.args_commonBooleanDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        if (provider.get(CommonNavConfig.class).result_commonBooleanDialog(navRoute)) {
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(DeleteCardCmd.class)
                                    .execute(mCard)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((card, throwable) -> {
                                        Context deleteContext = provider.getContext();
                                        if (throwable != null) {
                                            provider.get(ILogger.class)
                                                    .e(TAG,
                                                            deleteContext.getString(
                                                                    R.string.error_deleting_card),
                                                            throwable);
                                        } else {
                                            provider.get(ILogger.class)
                                                    .i(TAG,
                                                            deleteContext.getString(
                                                                    R.string.success_deleting_card, card.question));
                                        }
                                        compositeDisposable.dispose();
                                    })
                            );
                        }
                    });
        } else if (id == R.id.button_more_action) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.item_card, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();//showing popup menu
        } else if (id == R.id.image_question) {
            FileHelper fileHelper = mSvProvider.get(FileHelper.class);
            CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
            if (mCard != null && mCard.questionImage != null) {
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        commonNavConfig.args_commonImageView(
                                fileHelper.getCardQuestionImage(mCard.questionImage)
                        ));
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_move_card) {
            mNavigator.push(Routes.DECK_SELECT_DIALOG,
                    (navigator, navRoute, activity, currentView) -> {
                        DeckSelectSVDialog.Result result =
                                DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                        if (result != null) {
                            Deck selectedDeck = result.getSelectedDeck().get(0);
                            Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(DeckQueryCmd.class).getDeckById(mCard.deckId)
                                    .subscribe((deck, throwable) -> {
                                        if (throwable != null) {
                                            provider.get(ILogger.class)
                                                    .e(TAG, provider.getContext().getString(R.string.error_loading_deck), throwable);
                                            compositeDisposable.dispose();
                                        } else {
                                            compositeDisposable.add(provider.get(MoveCardCmd.class)
                                                    .execute(new MoveCardEvent(mCard.clone(), deck, selectedDeck))
                                                    .subscribe((moveCardEvent, throwable1) -> {
                                                        ILogger iLogger = provider.get(ILogger.class);
                                                        Context context = provider.getContext();
                                                        if (throwable1 != null) {
                                                            iLogger.e(TAG,
                                                                    context.getString(R.string.error_moving_card), throwable1);
                                                        } else {
                                                            iLogger.i(TAG,
                                                                    context.getString(R.string.success_moving_card,
                                                                            moveCardEvent.getDestinationDeck().name));
                                                        }
                                                        compositeDisposable.dispose();
                                                    })
                                            );
                                        }
                                    })
                            );
                        }
                    });
            return true;
        } else if (id == R.id.menu_copy_card) {
            mNavigator.push(Routes.DECK_SELECT_DIALOG,
                    (navigator, navRoute, activity, currentView) -> {
                        DeckSelectSVDialog.Result result =
                                DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                        if (result != null) {
                            Deck selectedDeck = result.getSelectedDeck().get(0);
                            Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(CopyCardCmd.class)
                                    .execute(new CopyCardEvent(mCard.clone(), selectedDeck))
                                    .subscribe((copyCardEvent, throwable1) -> {
                                        ILogger iLogger = provider.get(ILogger.class);
                                        Context context = provider.getContext();
                                        if (throwable1 != null) {
                                            iLogger.e(TAG,
                                                    context.getString(R.string.error_copying_card), throwable1);
                                        } else {
                                            iLogger.i(TAG,
                                                    context.getString(R.string.success_copying_card,
                                                            copyCardEvent.getDestinationDeck().name));
                                        }
                                        compositeDisposable.dispose();
                                    })
                            );
                        }
                    });
            return true;
        }
        return false;
    }
}
