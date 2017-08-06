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

import android.os.Bundle;
import android.support.annotation.Nullable;

/**
 * 在这里，Model层竟然抽象成了一个接口。可见Model一定不是java Bean 这么简单。
 * <p/>
 * Model 用来操作和存储数据，同时也提供了获取数据的方法。它给Presenter提供了一个接口，
 * 来让Presenter加载和更新Model中的数据。
 * <p/>
 * 它使用{@link QueryEnum}（它能够处理的查询列表，就是它能够理解的一些查询操作，其实说白了就是 请求数据 时的“协议”，
 * 例如 url 来请求网络数据；能够从ContentProvider中获取数据的Uri等）
 * 和{@link UserActionEnum}（它能够处理的 用户操作，比如某个UserActionEnum让它来删除一些数据）来作为参数。
 * <p/>
 * 通常情况下，一旦{@link Presenter}被创建，就会调用{@link #requestData(QueryEnum, DataQueryCallback)}
 * 来请求初始数据到Model中。本接口并没有定义Model如何来获取它自己的数据，但是它的一个实现类{@link ModelWithLoaderManager}
 * 提供了 通过{@link android.content.CursorLoader}从
 * {@link com.google.samples.apps.iosched.provider.ScheduleProvider}获取数据的操作。将来并不是仅仅通过
 * 上述方式来获取数据，但是现在很多数据都是这么获取的。
 * <p/>
 * 另外，当接收到一个{@link UserActionEnum}后，Model会同时更新 已经存储到本地的数据和它自己的数据，一般是通过
 * 触发{@link com.google.samples.apps.iosched.provider.ScheduleProvider}的 update 或者 insert 操作来实现的。
 * <p/>
 *
 * 通过上面的解释，可以看到，Model并不仅仅表示Java Bean，而是封装了对 特定数据的操作（查，通过QueryEnum来触发，
 * 增、删、改通过UserActionEnum来实现），但是它并没有提供业务逻辑，我估计业务逻辑需要根据具体业务再写一个 接口，
 * 或者直接写到 Presenter 中？
 * <p/>
 *
 * A Model is a class used to manipulate stored data, as well as provide getters for the data. It
 * provides the {@link Presenter} with an interface through which to load and update the data (MVP
 * architectural pattern).
 * <p/>
 * It is parametrised by the {@link QueryEnum} (the list of queries it is able to process) and the
 * {@link UserActionEnum} (the list of user actions it is able to process).
 * <p/>
 * Typically, the {@link Presenter} will call {@link #requestData(QueryEnum, DataQueryCallback)} at
 * least once when created, to load the initial data into the Model. This interface doesn't define
 * how the Model gets its data, but an implementation class {@link ModelWithLoaderManager} is
 * provided, obtaining the data from the {@link com.google.samples.apps.iosched.provider
 * .ScheduleProvider} by creating a {@link android.content.CursorLoader} and then parsing the
 * received {@link android.database.Cursor}. Not all features use this way of loading the data, but
 * a lot of them do.
 * <p/>
 * Additionally, when a {@link UserActionEnum} is received, the model updates both its own data and
 * the stored data, typically by making an update or insert call on the {@link
 * com.google.samples.apps .iosched.provider.ScheduleProvider}.
 * <p/>
 */
public interface Model<Q extends QueryEnum, UA extends UserActionEnum> {

    /**
     * @return an array of {@link QueryEnum} that can be processed by the model
     */
    public Q[] getQueries();

    /**
     * @return an array of {@link UserActionEnum} that can be processed by the model
     */
    public UA[] getUserActions();

    /**
     * Delivers a user {@code action} and associated {@code args} to the Model, which typically will
     * run a data update. The Model then notify the {@link Presenter} it is done with the user
     * action via the {@code callback}.
     * <p/>
     * Add the constants used to store values in the bundle to the Model implementation class as
     * final static protected strings.
     */
    public void deliverUserAction(UA action, @Nullable Bundle args, UserActionCallback callback);

    /**
     * Requests the Model to load data for the given {@code query}, then notify the data query was
     * completed via the {@code callback}. Typically, this is called to initialise the model with
     * the data needed to display the UI when loading.
     */
    public void requestData(Q query, DataQueryCallback callback);

    public void cleanUp();

    /**
     * A callback used to notify the {@link Presenter} that the update for a given {@link QueryEnum}
     * has completed, either successfully or with error.
     */
    public interface DataQueryCallback<M extends Model, Q extends QueryEnum> {

        public void onModelUpdated(M model, Q query);

        public void onError(Q query);
    }

    /**
     * A callback used to notify the {@link Presenter} that the update for a given {@link
     * UserActionEnum} has completed, either successfully or with error.
     */
    public interface UserActionCallback<M extends Model, UA extends UserActionEnum> {

        public void onModelUpdated(M model, UA userAction);

        public void onError(UA userAction);

    }
}
