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
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
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
    private transient AppShortcutHandler mAppShortcutHandler;
    private Deck mDeck;
    private transient BehaviorSubject<Deck> mDeckSubject;
    private transient BehaviorSubject<Integer> mDeckCardCountSubject;
    private ListMode mListMode;
    private boolean mIsSelected;
    private transient CompoundButton mSelectedUiButton;
    private transient OnItemSelectListener mOnItemSelectListener;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mAppShortcutHandler = mSvProvider.get(AppShortcutHandler.class);
    }

    public DeckItemSV(ListMode listMode) {
        mListMode = listMode;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        initDeckSubject();
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
                radioSelect.setChecked(mIsSelected);
                mSelectedUiButton = radioSelect;
            } else if (mListMode.mSelectMode == ListMode.MULTI_SELECT_MODE) {
                checkBoxSelect.setVisibility(View.VISIBLE);
                checkBoxSelect.setChecked(mIsSelected);
                mSelectedUiButton = checkBoxSelect;
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
        mSvProvider.get(RxDisposer.class).add("createView_onChangeDeck",
                mDeckSubject.observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deck -> textDeckName.setText(deck.name)));
        mSvProvider.get(RxDisposer.class).add("createView_onChangeCardCount",
                mDeckCardCountSubject.observeOn(AndroidSchedulers.mainThread())
                        .subscribe(integer -> {
                            Context context = mSvProvider.getContext();
                            textTotalCards.setText(context.getString(R.string.total_cards, integer));
                        }));
        mSvProvider.get(RxDisposer.class).add("createView_onCardAdded",
                mSvProvider.get(DeckChangeNotifier.class).getAddedCardFlow()
                        .subscribe(card -> {
                            if (card.deckId.equals(mDeck.id)) {
                                loadCardCount();
                            }
                        }));
        mSvProvider.get(RxDisposer.class).add("createView_onCardDeleted",
                mSvProvider.get(DeckChangeNotifier.class).getDeletedCardFlow()
                        .subscribe(card -> {
                            if (card.deckId.equals(mDeck.id)) {
                                loadCardCount();
                            }
                        }));
        mSvProvider.get(RxDisposer.class).add("createView_onCardMoved",
                mSvProvider.get(DeckChangeNotifier.class).getMovedCardFlow()
                        .subscribe(moveCardEvent -> {
                            Deck sourceDeck = moveCardEvent.getSourceDeck();
                            Deck destDeck = moveCardEvent.getDestinationDeck();
                            if (sourceDeck.id.equals(mDeck.id) || destDeck.id.equals(mDeck.id)) {
                                loadCardCount();
                            }
                        }));
        return rootLayout;
    }

    private void initDeckSubject() {
        if (mDeckSubject == null) {
            if (mDeck != null) {
                mDeckSubject = BehaviorSubject.createDefault(mDeck);
            } else {
                mDeckSubject = BehaviorSubject.create();
            }
        } else {
            mDeckSubject.onNext(mDeck);
        }
        if (mDeckCardCountSubject == null) {
            mDeckCardCountSubject = BehaviorSubject.createDefault(0);
        }
    }

    private void loadCardCount() {
        mSvProvider.get(RxDisposer.class).add("loadCardCount_queryCardCount",
                mSvProvider.get(DeckQueryCmd.class).countCards(mDeck)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((integer, throwable) -> {
                            if (throwable != null) {
                                Context context = mSvProvider.getContext();
                                mSvProvider.get(ILogger.class).e(TAG, context.getString(R.string.error_counting_cards), throwable);
                            } else {
                                if (mDeckCardCountSubject != null) {
                                    mDeckCardCountSubject.onNext(integer);
                                } else {
                                    mDeckCardCountSubject = BehaviorSubject.createDefault(integer);
                                }
                            }
                        }));
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        if (mDeckSubject != null) {
            mDeckSubject.onComplete();
            mDeckSubject = null;
        }
        mDeck = null;
        mListMode = null;
        mSelectedUiButton = null;
    }

    public void setDeck(Deck deck) {
        mDeck = deck;
        initDeckSubject();
        loadCardCount();
    }

    public Deck getDeck() {
        return mDeck;
    }

    public ListMode getListMode() {
        return mListMode;
    }

    public void select() {
        mIsSelected = true;
        if (mSelectedUiButton != null) {
            mSelectedUiButton.setChecked(true);
        }
    }

    public void unSelect() {
        mIsSelected = false;
        if (mSelectedUiButton != null) {
            mSelectedUiButton.setChecked(false);
        }
    }

    public void setOnSelectListener(OnItemSelectListener onItemSelectListener) {
        mOnItemSelectListener = onItemSelectListener;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.root_layout) {
            if (mListMode != null) {
                if (mListMode.mSelectMode == ListMode.MULTI_SELECT_MODE) {
                    mIsSelected = !mIsSelected;
                } else {
                    mIsSelected = true;
                }
                if (mSelectedUiButton != null) {
                    mSelectedUiButton.setChecked(mIsSelected);
                }
                if (mOnItemSelectListener != null) {
                    mOnItemSelectListener.onItemSelect(mDeck, mIsSelected);
                }
            } else {
                mNavigator.push(Routes.CARDS, CardListPage.Args.withDeck(mDeck.clone()));
            }
        } else if (viewId == R.id.button_edit) {
            mNavigator.push(Routes.DECK_DETAIL_DIALOG,
                    DeckDetailSVDialog.Args.forUpdate(mDeck.clone()),
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
            String content = context.getString(R.string.confirm_delete_deck, mDeck.name);
            CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
            mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                    commonNavConfig.args_commonBooleanDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        if (provider.get(CommonNavConfig.class).result_commonBooleanDialog(navRoute)) {
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(DeleteDeckCmd.class)
                                    .execute(mDeck)
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
                mAppShortcutHandler.createPinnedShortcut(mDeck);
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
