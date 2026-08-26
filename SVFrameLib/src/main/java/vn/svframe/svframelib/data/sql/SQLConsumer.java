package vn.svframe.svframelib.data.sql; import java.sql.*; @FunctionalInterface public interface SQLConsumer { void accept(ResultSet result) throws SQLException; }
