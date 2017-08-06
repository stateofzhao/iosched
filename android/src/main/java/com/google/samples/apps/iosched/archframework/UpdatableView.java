/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.samples.apps.iosched.archframework;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;

/*
* 这个View接口，有一个相应用户操作的接口，可以让Presenter来是实现此接口，然后能够让View直接控制Presenter了，
* 这样做的好处就是能够让 Presenter 更抽象，而且不必让Presenter实现类来写更多针对 用户操作 的方法。
*
* */

/**
 * UpdatableView 是一个UI接口，通常是一个{@link android.app.Fragment}，{@link android.app.Fragment}会
 * 提供一个{@link Presenter}（一个用于控制UpdatableView的接口）。
 * <p/>
 * 它使用{@link Model}、{@link QueryEnum}和{@link UserActionEnum}来作为参数。
 * <p/>
 * 一旦Model中的数据请求完成，{@link Presenter}通过调用{@link #displayData(Object, QueryEnum)}或者
 * {@link #displayErrorMessage(QueryEnum)}来更新UpdatableView。
 * <p/>
 * {@link Presenter}会作为{@link UserActionListener}通过调用{@link #addListener(UserActionListener)}
 * 方法将它注册给UpdatableView，这样当用户执行一个操作时，就能够触发{@link Model}的更新，当data更新后，
 * {@link Presenter}通过调用UpdatableView的{@link #displayUserActionResult(Object, UserActionEnum, boolean)}
 * 方法来更新UpdatableView。
 * <p/>
 *
 * An UpdatableView is a UI class, often a {@link android.app.Fragment}, that provides a {@link
 * Presenter} an interface through which to control it  (MVP architectural pattern).
 * It is
 * parametrised by the {@link Model} class, the {@link QueryEnum} (the list of queries it needs to
 * run to display its initial state) and the {@link UserActionEnum} (the list of user actions it
 * provides to the user).
 * <p/>
 * Once the data queries are loaded in the Model, the {@link Presenter} updates the UpdatableView by
 * calling {@link #displayData(Object, QueryEnum)} or {@link #displayErrorMessage(QueryEnum)} so the
 * UpdatableView can redraw itself with the updated data.
 * <p/>
 * The {@link Presenter} registers itself as a {@link UserActionListener} with {@link
 * #addListener(UserActionListener)}, so that it can trigger an update on the {@link Model} when the
 * user performs an action on the UpdatableView. After the data is updated, the {@link Presenter}
 * updates the UpdatableView by calling {@link #displayUserActionResult(Object, UserActionEnum,
 * boolean)}.
 * <p/>
 * The UpdatableView belongs to an {@link android.app.Activity} that typically has been started with
 * an {@link android.content.Intent} specifying at least one Data URI, used for loading the initial
 * data into the {@link Model}.
 */
public interface UpdatableView<M, Q extends QueryEnum, UA extends UserActionEnum> {

    /**
     * Updates the view based on data in the model.
     *
     * @param model The updated model.
     * @param query The query that has triggered the model update. This is so not the full view has
     *              to be updated but only specific elements of the view, depending on the query.
     */
    public void displayData(M model, Q query);

    /**
     * Displays error message resulting from a query not succeeding.
     *
     * @param query The query that resulted in an error.
     */
    public void displayErrorMessage(Q query);

    /**
     * Updates the view based on the data in the model, following a user action and a success status
     * in updating the data.
     * <p/>
     * When a user action has been carried out, quite often, the View is already up to date with the
     * action (eg a checked box), so this method is typically used to show messages, such as a Toast
     * to confirm the success of an operation.
     *
     * @param model      The updated model.
     * @param userAction The user action that has triggered the model updated. This is so not the
     *                   full view has to be updated but only specific elements of the view,
     *                   depending on the user action.
     * @param success    Whether the user action was carried out successfully in the model.
     */
    public void displayUserActionResult(M model, UA userAction, boolean success);

    /**
     * Data URI representing the data displayed in this view. Complex views may use data from
     * different queries / Data URI.
     *
     * @param query The query for which the URI should be returned.
     */
    public Uri getDataUri(Q query);

    public Context getContext();

    public void addListener(UserActionListener listener);

    /**
     * A listener for events fired off by a {@link Model}
     */
    interface UserActionListener<UA extends UserActionEnum> {

        /**
         * Called when the user has performed an {@code action}, with data to be passed as a {@link
         * android.os.Bundle} in {@code args}.
         * <p/>
         * Add the constants used to store values in the bundle to the Model implementation class as
         * final static protected strings.
         */
        public void onUserAction(UA action, @Nullable Bundle args);
    }
}