package io.lumine.mythic.lib.data.sql; import java.sql.*; @FunctionalInterface public interface SQLConsumer { void accept(ResultSet result) throws SQLException; }
