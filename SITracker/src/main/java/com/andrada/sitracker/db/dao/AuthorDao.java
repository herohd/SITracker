/*
 * Copyright 2014 Gleb Godonoga.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrada.sitracker.db.dao;

import com.andrada.sitracker.db.beans.Author;
import com.j256.ormlite.dao.Dao;

import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;

public interface AuthorDao extends Dao<Author, Integer> {

    @NotNull
    List<String> getAuthorsUrls() throws SQLException;

    @NotNull
    List<String> getAuthorsUrlIds() throws SQLException;

    boolean hasAuthor(String authorUrlId) throws SQLException;

    int getNewAuthorsCount() throws SQLException;

    void markAsRead(Author author) throws SQLException;

    @NotNull
    List<Author> getAllAuthorsSortedNew() throws SQLException;

    @NotNull
    List<Author> getAllAuthorsSortedAZ() throws SQLException;

    void removeAuthor(long id) throws SQLException;
}
