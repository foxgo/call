package com.callcenter.iam.config;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.internal.database.DatabaseType;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMySql84SupportTest {

    @Test
    void shouldRecognizeMySql84DatabaseProductName() {
        DatabaseType databaseType = DatabaseTypeRegister.getDatabaseTypeForConnection(connection("MySQL 8.4", "8.4.0"));

        assertThat(databaseType.getName()).isEqualTo("MySQL");
    }

    private static Connection connection(String productName, String productVersion) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                FlywayMySql84SupportTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new java.lang.reflect.InvocationHandler() {
                    private boolean unread = true;

                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                        return switch (method.getName()) {
                            case "next" -> unread ? (unread = false) || true : false;
                            case "getString" -> productVersion;
                            default -> defaultValue(method.getReturnType());
                        };
                    }
                }
        );

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                FlywayMySql84SupportTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> resultSet;
                    default -> defaultValue(method.getReturnType());
                }
        );

        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
                FlywayMySql84SupportTest.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDatabaseProductName" -> productName;
                    case "getDatabaseProductVersion" -> productVersion;
                    case "getURL" -> "jdbc:mysql://localhost:3306/call_iam";
                    default -> defaultValue(method.getReturnType());
                }
        );

        return (Connection) Proxy.newProxyInstance(
                FlywayMySql84SupportTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMetaData" -> metaData;
                    case "isClosed" -> false;
                    case "prepareStatement" -> statement;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (void.class.equals(returnType)) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        throw new IllegalArgumentException("Unsupported primitive type: " + returnType);
    }
}
