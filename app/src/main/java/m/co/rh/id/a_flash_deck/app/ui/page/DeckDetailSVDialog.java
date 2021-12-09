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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.StatefulViewProvider;
import m.co.rh.id.a_flash_deck.app.provider.command.NewDeckCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateDeckCmd;
import m.co.rh.id.a_flash_deck.app.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class DeckDetailSVDialog extends StatefulViewDialog<Activity> implements View.OnClickListener, TextWatcher {
    private static String TAG = DeckDetailSVDialog.class.getName();

    @NavInject
    private transient NavRoute mNavRoute;
    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private transient NewDeckCmd mNewDeckCmd;
    private Deck mDeck;
    private String mTitle;
    private transient BehaviorSubject<String> mTitleSubject;
    private transient BehaviorSubject<String> mNameSubject;

    public DeckDetailSVDialog() {
        // no need navigator as this will be used as main route and will not be re-used as other SV component
        super(null);
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mDeck = new Deck();
        mDeck.name = "";
        Args args = Args.of(mNavRoute);
        if (args != null) {
            if (args.isUpdate()) {
                mDeck = args.getDeck();
            }
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(StatefulViewProvider.class);
        mTitle = activity.getString(R.string.add_deck);
        View view = activity.getLayoutInflater().inflate(R.layout.deck_detail, container, false);
        setTitleSubject();
        setNameSubject();
        TextView textViewTitle = view.findViewById(R.id.text_view_title);
        EditText editTextName = view.findViewById(R.id.edit_text_name);
        editTextName.addTextChangedListener(this);
        Button buttonCancel = view.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        Button buttonSave = view.findViewById(R.id.button_save);
        buttonSave.setOnClickListener(this);
        if (mNewDeckCmd == null) {
            Args args = Args.of(mNavRoute);
            if (args != null && args.isUpdate()) {
                mNewDeckCmd = mSvProvider.get(UpdateDeckCmd.class);
            } else {
                mNewDeckCmd = mSvProvider.get(NewDeckCmd.class);
            }
        }
        mSvProvider.get(RxDisposer.class).add("titleChanged", mTitleSubject.subscribe(
                textViewTitle::setText));
        mSvProvider.get(RxDisposer.class).add("nameChanged", mNameSubject.subscribe(
                editTextName::setText));
        mSvProvider.get(RxDisposer.class).add("nameValid",
                mNewDeckCmd.getNameValidation()
                        .subscribe(s -> {
                            if (s.isEmpty()) {
                                editTextName.setError(null);
                            } else {
                                editTextName.setError(s);
                            }
                        }));
        return view;
    }

    @Override
    protected Dialog createDialog(Activity activity) {
        MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(activity);
        materialAlertDialogBuilder.setCancelable(true);
        materialAlertDialogBuilder.setView(buildView(activity, null));
        return materialAlertDialogBuilder.create();
    }

    @Override
    protected void onCancelDialog(DialogInterface dialog) {
        clear();
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNewDeckCmd = null;
        mNavRoute = null;
        mDeck = null;
        mTitle = null;
        if (mTitleSubject != null) {
            mTitleSubject.onComplete();
            mTitleSubject = null;
        }
        if (mNameSubject != null) {
            mNameSubject.onComplete();
            mNameSubject = null;
        }
    }

    private void clear() {
        mDeck.name = "";
        setNameSubject();
    }

    private void setNameSubject() {
        String name = mDeck.name;
        if (mNameSubject == null) {
            mNameSubject = BehaviorSubject.createDefault(name);
        } else {
            mNameSubject.onNext(name);
        }
    }

    private void setTitleSubject() {
        if (mTitleSubject == null) {
            mTitleSubject = BehaviorSubject.createDefault(mTitle);
        } else {
            mTitleSubject.onNext(mTitle);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_save) {
            if (mNewDeckCmd.valid(mDeck)) {
                mSvProvider.get(RxDisposer.class)
                        .add("newDeck", mNewDeckCmd.execute(mDeck)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((deck, throwable) -> {
                                    Context context = mSvProvider.getContext();
                                    String errorMessage;
                                    String successMessage;
                                    Args args = Args.of(mNavRoute);
                                    if (args != null && args.isUpdate()) {
                                        errorMessage = context.getString(R.string.error_updating_deck);
                                        successMessage = context.getString(R.string.success_updating_deck, deck.name);
                                    } else {
                                        errorMessage = context.getString(R.string.error_adding_new_deck);
                                        successMessage = context.getString(R.string.success_adding_new_deck, deck.name);
                                    }
                                    if (throwable != null) {
                                        mSvProvider.get(ILogger.class)
                                                .e(TAG, errorMessage, throwable);
                                    } else {
                                        mSvProvider.get(ILogger.class)
                                                .i(TAG, successMessage);
                                    }
                                    getNavigator().pop(Result.newResult(deck)); // use pop to dismiss current dialog
                                }));
            }
        } else if (id == R.id.button_cancel) {
            getNavigator().pop();
        }
    }

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
        mDeck.name = editable.toString();
        mNewDeckCmd.valid(mDeck);
    }

    /**
     * Argument for this Dialog
     */
    public static class Args implements Serializable {
        public static Args forUpdate(Deck deck) {
            Args args = new Args();
            args.mDeck = deck;
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

    /**
     * Result for this dialog
     */
    public static class Result implements Serializable {
        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        public static Result newResult(Deck deck) {
            Result result = new Result();
            result.mDeck = deck;
            return result;
        }

        private Deck mDeck;

        private Result() {
        }

        public Deck getDeck() {
            return mDeck;
        }
    }
}
