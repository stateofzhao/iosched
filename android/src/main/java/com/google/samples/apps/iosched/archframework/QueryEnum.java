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

/**
 * 只是提供了查询唯一标识和需要查询的内容（哪些数据），并没有提供要从哪里查询（目标Uri）。
 * 这样分工很好，我不关心要从哪里查询，我只关心内容。从哪里查询那是Model的职责，这样更自由。
 * <p/>
 *
 *
 * Represents a data query which typically is carried out using the content provider {@link
 * com.google.samples.apps.iosched.provider.ScheduleProvider}.
 */
public interface QueryEnum {

    /**
     * 获取查询id，这个需要自定义，在iosched中，Model都是会提前定义好自己所有支持的QueryEnum，
     * 在每次调用Model的查询方法（可以理解为加载数据的方法）时，都会将传入的
     * QueryEnum（初始化请求{@link Presenter#loadInitialQueries()}）或者 直接传入的id（参见{@link
     * ModelWithLoaderManager#deliverUserAction(UserActionEnum, Bundle, Model.UserActionCallback)}）
     * 来匹配提前定义好的QueryEnum 中是否包含此id，如果包含，则才会进行数据请求操作，否则回调错误接口。
     *
     * @return the id of the query
     */
    public int getId();

    /**
     * 这个不要理解错误，在iosched中，这个时一次请求需要查询的字段名，也就是 数据库中表的列名。并不是之前理解的
     * 一个{@link #getId()}对应的一组请求。
     *
     * @return the projection for the query. The fields in the projection are defined in the {@link
     * com.google.samples.apps.iosched.provider.ScheduleContract}. This field may be null if the
     * query is not to be carried on the {@link com.google.samples.apps.iosched.provider
     * .ScheduleProvider}
     */
    public String[] getProjection();

}
