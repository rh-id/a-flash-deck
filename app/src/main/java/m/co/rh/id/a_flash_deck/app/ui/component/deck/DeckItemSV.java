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

package m.co.rh.id.a_flash_deck.app.ui.component.deck;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import java.io.Serializable;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.DeckQueryCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.DeleteDeckCmd;
import m.co.rh.id.a_flash_deck.app.provider.component.AppShortcutHandler;
import m.co.rh.id.a_flash_deck.app.ui.page.CardListPage;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckDetailSVDialog;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class DeckItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = DeckItemSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient CommonNavConfig mCommonNavConfig;
    private transient DeckChangeNotifier mDeckChangeNotifier;
    private transient AppShortcutHandler mAppShortcutHandler;
    private transient DeckQueryCmd mDeckQueryCmd;

    private SerialBehaviorSubject<Deck> mDeck;
    private SerialBehaviorSubject<Integer> mDeckCardCount;
    private ListMode mListMode;
    private SerialBehaviorSubject<Boolean> mIsSelected;
    private transient OnItemSelectListener mOnItemSelectListener;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mDeckChangeNotifier = mSvProvider.get(DeckChangeNotifier.class);
        mAppShortcutHandler = mSvProvider.get(AppShortcutHandler.class);
        mDeckQueryCmd = mSvProvider.get(DeckQueryCmd.class);
    }

    public DeckItemSV(ListMode listMode) {
        mListMode = listMode;
        mDeck = new SerialBehaviorSubject<>();
        mDeckCardCount = new SerialBehaviorSubject<>(0);
        mIsSelected = new SerialBehaviorSubject<>(false);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.item_deck, container, false);
        rootLayout.setOnClickListener(this);
        RadioButton radioSelect = rootLayout.findViewById(R.id.radio_select);
        CheckBox checkBoxSelect = rootLayout.findViewById(R.id.checkbox_select);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        Button buttonDelete = rootLayout.findViewById(R.id.button_delete);
        Button buttonMore = rootLayout.findViewById(R.id.button_more_action);
        if (mListMode != null) {
            buttonEdit.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);
            buttonMore.setVisibility(View.GONE);
            if (mListMode.mSelectMode == ListMode.SELECT_MODE) {
                radioSelect.setVisibility(View.VISIBLE);
            } else if (mListMode.mSelectMode == ListMode.MULTI_SELECT_MODE) {
                checkBoxSelect.setVisibility(View.VISIBLE);
            }
        } else {
            buttonEdit.setVisibility(View.VISIBLE);
            buttonDelete.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ShortcutManager shortcutManager = activity.getSystemService(ShortcutManager.class);
                if (shortcutManager.isRequestPinShortcutSupported()) {
                    buttonMore.setVisibility(View.VISIBLE);
                }
            } else {
                buttonMore.setVisibility(View.GONE);
            }
        }
        buttonEdit.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        buttonMore.setOnClickListener(this);
        TextView textDeckName = rootLayout.findViewById(R.id.text_deck_name);
        TextView textTotalCards = rootLayout.findViewById(R.id.text_total_cards);
        mRxDisposer.add("createView_onChangeDeck",
                mDeck.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deck -> textDeckName.setText(deck.name)));
        mRxDisposer.add("createView_onChangeCardCount",
                mDeckCardCount.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(integer -> {
                            Context context = mSvProvider.getContext();
                            textTotalCards.setText(context.getString(R.string.total_cards, integer));
                        }));
        mRxDisposer.add("createView_onIsSelectedChanged",
                mIsSelected.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (mListMode != null) {
                                if (mListMode.mSelectMode == ListMode.SELECT_MODE) {
                                    radioSelect.setChecked(aBoolean);
                                } else if (mListMode.mSelectMode == ListMode.MULTI_SELECT_MODE) {
                                    checkBoxSelect.setChecked(aBoolean);
                                }
                            }
                        }));
        mRxDisposer.add("createView_onCardAdded",
                mDeckChangeNotifier.getAddedCardFlow()
                        .subscribe(card -> {
                            if (card.deckId.equals(mDeck.getValue().id)) {
                                loadCardCount();
                            }
                        }));
        mRxDisposer.add("createView_onCardDeleted",
                mDeckChangeNotifier.getDeletedCardFlow()
                        .subscribe(card -> {
                            if (card.deckId.equals(mDeck.getValue().id)) {
                                loadCardCount();
                            }
                        }));
        mRxDisposer.add("createView_onCardMoved",
                mDeckChangeNotifier.getMovedCardFlow()
                        .subscribe(moveCardEvent -> {
                            Deck sourceDeck = moveCardEvent.getSourceDeck();
                            Deck destDeck = moveCardEvent.getDestinationDeck();
                            Deck currentDeck = mDeck.getValue();
                            if (sourceDeck.id.equals(currentDeck.id) || destDeck.id.equals(currentDeck.id)) {
                                loadCardCount();
                            }
                        }));
        return rootLayout;
    }

    private void loadCardCount() {
        mRxDisposer.add("loadCardCount_queryCardCount",
                mDeckQueryCmd.countCards(mDeck.getValue())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((integer, throwable) -> {
                            if (throwable != null) {
                                Context context = mSvProvider.getContext();
                                mLogger.e(TAG, context.getString(R.string.error_counting_cards), throwable);
                            } else {
                                mDeckCardCount.onNext(integer);
                            }
                        }));
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mListMode = null;
    }

    public void setDeck(Deck deck) {
        mDeck.onNext(deck);
        loadCardCount();
    }

    public Deck getDeck() {
        return mDeck.getValue();
    }

    public ListMode getListMode() {
        return mListMode;
    }

    public void select() {
        mIsSelected.onNext(true);
    }

    public void unSelect() {
        mIsSelected.onNext(false);
    }

    public void setOnSelectListener(OnItemSelectListener onItemSelectListener) {
        mOnItemSelectListener = onItemSelectListener;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.root_layout) {
            if (mListMode != null) {
                boolean isSelected = mIsSelected.getValue();
                if (mListMode.mSelectMode == ListMode.MULTI_SELECT_MODE) {
                    isSelected = !isSelected;
                } else {
                    isSelected = true;
                }
                mIsSelected.onNext(isSelected);
                if (mOnItemSelectListener != null) {
                    mOnItemSelectListener.onItemSelect(mDeck.getValue(), isSelected);
                }
            } else {
                mNavigator.push(Routes.CARDS, CardListPage.Args.withDeck(mDeck.getValue().clone()));
            }
        } else if (viewId == R.id.button_edit) {
            mNavigator.push(Routes.DECK_DETAIL_DIALOG,
                    DeckDetailSVDialog.Args.forUpdate(mDeck.getValue().clone()),
                    (navigator, navRoute, activity, currentView) -> {
                        DeckDetailSVDialog.Result result =
                                DeckDetailSVDialog.Result.of(navRoute.getRouteResult());
                        if (result != null) {
                            setDeck(result.getDeck());
                        }
                    });
        } else if (viewId == R.id.button_delete) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_deck, mDeck.getValue().name);
            mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                    mCommonNavConfig.args_commonBooleanDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        if (provider.get(CommonNavConfig.class).result_commonBooleanDialog(navRoute)) {
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(DeleteDeckCmd.class)
                                    .execute(mDeck.getValue())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((deck, throwable) -> {
                                        Context deleteContext = provider.getContext();
                                        if (throwable != null) {
                                            provider.get(ILogger.class)
                                                    .e(TAG,
                                                            deleteContext.getString(
                                                                    R.string.error_deleting_deck),
                                                            throwable);
                                        } else {
                                            provider.get(ILogger.class)
                                                    .i(TAG,
                                                            deleteContext.getString(
                                                                    R.string.success_deleting_deck, deck.name));
                                        }
                                    })
                            );
                        }
                    });
        } else if (viewId == R.id.button_more_action) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.item_deck, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();//showing popup menu
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_create_shuffle_shortcut) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mAppShortcutHandler.createPinnedShortcut(mDeck.getValue());
            }
            return true;
        }
        return false;
    }

    /**
     * Interface to handle if this deck is selected or not event
     */
    public interface OnItemSelectListener {
        void onItemSelect(Deck deck, boolean selected);
    }

    public static class ListMode implements Serializable {
        public static ListMode multiSelectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = MULTI_SELECT_MODE;
            return listMode;
        }

        public static ListMode selectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = SELECT_MODE;
            return listMode;
        }

        /**
         * Selection with radio button or only one selection
         */
        private static final byte SELECT_MODE = 0;
        /**
         * Selection with checkbox button for multiple selection
         */
        private static final byte MULTI_SELECT_MODE = 1;

        private byte mSelectMode;

        private ListMode() {
        }

        public boolean shouldClearOtherSelection() {
            return mSelectMode == SELECT_MODE;
        }

        public int getSelectMode() {
            return mSelectMode;
        }
    }
}
