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

package m.co.rh.id.a_flash_deck.base.constants;

public class Routes {
    public static final String SPLASH_PAGE = "splash";
    public static final String HOME_PAGE = "/";
    public static final String SETTINGS_PAGE = "/settings";
    public static final String DONATIONS_PAGE = "/donations";
    public static final String CARD_DETAIL_PAGE = "/card/detail";
    public static final String CARD_SHOW_HOME_PAGE = "/cardShow";
    public static final String CARD_SHOW_PAGE = "/card/show";
    public static final String DECK_DETAIL_DIALOG = "/deck/detailDialog";
    public static final String DECK_SELECT_DIALOG = "/deck/selectDialog";
    public static final String DECKS = "/decks";
    public static final String CARDS = "/cards";
    public static final String TEST = "/test";
    public static final String NOTIFICATION_TIMERS = "/notificationTimers";
    public static final String NOTIFICATION_TIMER_DETAIL_DIALOG = "/notificationTimer/detailDialog";

    //Use CommonNavConfig.java to handle arguments and result return by common routes
    public static final String COMMON_BOOLEAN_DIALOG = "/common/booleanDialog";
    public static final String COMMON_MESSAGE_DIALOG = "/common/messageDialog";
    public static final String COMMON_TIMEPICKER_DIALOG = "/common/timePickerDialog";
    public static final String COMMON_IMAGEVIEW = "/common/imageView";
    public static final String COMMON_VOICERECORD = "/common/voiceRecord";

    private Routes() {
    }
}
