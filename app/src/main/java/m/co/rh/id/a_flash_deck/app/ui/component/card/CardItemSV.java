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

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
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
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class CardItemSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = CardItemSV.class.getName();

    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient FileHelper mFileHelper;
    private transient CommonNavConfig mCommonNavConfig;
    private transient DeckChangeNotifier mDeckChangeNotifier;
    private transient RxDisposer mRxDisposer;
    private transient DeckQueryCmd mDeckQueryCmd;

    private SerialBehaviorSubject<Card> mCardSubject;

    public CardItemSV() {
        mCardSubject = new SerialBehaviorSubject<>();
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mDeckChangeNotifier = mSvProvider.get(DeckChangeNotifier.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mDeckQueryCmd = mSvProvider.get(DeckQueryCmd.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
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
        mRxDisposer.add("createView_onChangeCard",
                mCardSubject.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(card -> {
                            Context context = mSvProvider.getContext();
                            textQuestion.setText(context.getString(R.string.question_desc_value, card.question));
                            textAnswer.setText(context.getString(R.string.answer_desc_value, card.answer));
                            if (card.questionImage != null) {
                                imageQuestion.setImageURI(Uri.fromFile(mFileHelper.getCardQuestionImageThumbnail(card.questionImage)));
                                imageQuestion.setVisibility(View.VISIBLE);
                            } else {
                                imageQuestion.setImageURI(null);
                                imageQuestion.setVisibility(View.GONE);
                            }
                        }));
        mRxDisposer.add("createView_onChangeCard_getDeckById",
                mCardSubject.getSubject().switchMapSingle(card ->
                        mDeckQueryCmd.getDeckById(card.deckId)
                                .observeOn(AndroidSchedulers.mainThread())
                ).subscribe(deck -> {
                    textDeckName.setText(deck.name);
                }, throwable -> {
                    mLogger.e(TAG, mSvProvider.getContext().getString(R.string.error_loading_deck), throwable);
                }));
        mRxDisposer.add("createView_onMoveCard",
                mDeckChangeNotifier
                        .getMovedCardFlow()
                        .filter(moveCardEvent -> moveCardEvent.getMovedCard().id.equals(getCard().id))
                        .switchMapSingle(moveCardEvent ->
                                mDeckQueryCmd
                                        .getDeckById(moveCardEvent.getDestinationDeck().id)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .map(deck -> new Object[]{moveCardEvent, deck})
                        ).subscribe(objects -> {
                            MoveCardEvent moveCardEvent = (MoveCardEvent) objects[0];
                            Deck deck = (Deck) objects[1];
                            Context context = mSvProvider.getContext();
                            textQuestion.setText(context.getString(R.string.question_desc_value, moveCardEvent.getMovedCard().question));
                            textAnswer.setText(context.getString(R.string.answer_desc_value, moveCardEvent.getMovedCard().answer));
                            textDeckName.setText(deck.name);
                        }, throwable -> {
                            mLogger.e(TAG, mSvProvider.getContext().getString(R.string.error_loading_deck), throwable);
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mNavigator = null;
    }

    public void setCard(Card card) {
        if (card != null) {
            mCardSubject.onNext(card);
        }
    }

    public Card getCard() {
        return mCardSubject.getValue();
    }

    @Override
    public void onClick(View view) {
        Card card = getCard();
        int id = view.getId();
        if (id == R.id.button_edit) {
            mNavigator.push(Routes.CARD_DETAIL_PAGE, CardDetailPage.Args.forUpdate(card.clone()),
                    (navigator, navRoute, activity, currentView) -> {
                        CardDetailPage.Result result = CardDetailPage.Result.of(navRoute.getRouteResult());
                        if (result != null) {
                            setCard(result.getCard());
                        }
                    });
        } else if (id == R.id.button_delete) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_card, card.question);
            mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                    mCommonNavConfig.args_commonBooleanDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        if (provider.get(CommonNavConfig.class).result_commonBooleanDialog(navRoute)) {
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(DeleteCardCmd.class)
                                    .execute(card)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((deletedCard, throwable) -> {
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
                                                                    R.string.success_deleting_card, deletedCard.question));
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
            popup.show();
        } else if (id == R.id.image_question) {
            if (card != null && card.questionImage != null) {
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        mCommonNavConfig.args_commonImageView(
                                mFileHelper.getCardQuestionImage(card.questionImage)
                        ));
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_move_card) {
            moveCardAction(mNavigator, getCard().clone());
            return true;
        } else if (id == R.id.menu_copy_card) {
            copyCardAction(mNavigator, getCard().clone());
            return true;
        }
        return false;
    }

    public static void copyCardAction(INavigator mainNavigator, Card card) {
        mainNavigator.push(Routes.DECK_SELECT_DIALOG,
                (navigator, navRoute, activity, currentView) -> {
                    DeckSelectSVDialog.Result result =
                            DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                    if (result != null) {
                        Deck selectedDeck = result.getSelectedDeck().get(0);
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        CompositeDisposable compositeDisposable = new CompositeDisposable();
                        compositeDisposable.add(provider.get(CopyCardCmd.class)
                                .execute(new CopyCardEvent(card, selectedDeck))
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
    }

    public static void moveCardAction(INavigator mainNavigator, Card card) {
        mainNavigator.push(Routes.DECK_SELECT_DIALOG,
                (navigator, navRoute, activity, currentView) -> {
                    DeckSelectSVDialog.Result result =
                            DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                    if (result != null) {
                        Deck selectedDeck = result.getSelectedDeck().get(0);
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        CompositeDisposable compositeDisposable = new CompositeDisposable();
                        compositeDisposable.add(provider.get(DeckQueryCmd.class).getDeckById(card.deckId)
                                .subscribe((deck, throwable) -> {
                                    if (throwable != null) {
                                        provider.get(ILogger.class)
                                                .e(TAG, provider.getContext().getString(R.string.error_loading_deck), throwable);
                                        compositeDisposable.dispose();
                                    } else {
                                        compositeDisposable.add(provider.get(MoveCardCmd.class)
                                                .execute(new MoveCardEvent(card, deck, selectedDeck))
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
    }
}
