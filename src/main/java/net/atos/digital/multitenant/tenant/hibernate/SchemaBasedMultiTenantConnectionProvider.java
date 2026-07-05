

package net.atos.digital.multitenant.tenant.hibernate;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaSelector;
import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.MessageFormat;

@Slf4j
public class SchemaBasedMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final TenantContext tenantContext;
    private SchemaSelector schemaSelector;

    public SchemaBasedMultiTenantConnectionProvider(DataSource dataSource, TenantContext tenantContext, SchemaSelector schemaSelector) {
        this.dataSource = dataSource;
        this.tenantContext = tenantContext;
        this.schemaSelector = schemaSelector;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        if (tenantContext.getCurrentTenant() != null) {
            setSchema(tenantContext.getCurrentSchemaName(), connection);
        }
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String schemaName) throws SQLException {
        Connection connection = getAnyConnection();
        setSchema(schemaName, connection);
        return connection;
    }

    private void setSchema(String schemaName, Connection connection) {
        //String schemaName = schemaPrefix.concat("_").concat(tenantIdentifier).toLowerCase();
        // bridage de sécurité pour éviter les injections SQL : que des lettres et des _
        if (!schemaName.matches("^[0-9A-Za-z_]*$")) {
            throw new IllegalArgumentException("Schema name is malformed: " + schemaName);
        }
        try {
            // TRACE : ce log est émis à chaque acquisition de connexion — en INFO il noie les logs applicatifs.
            log.trace("selected schema : {}", schemaName);
            // rendre accessible la base de données du tenant
            PreparedStatement preparedStatement = connection.prepareStatement(schemaSelector.apply(schemaName));
            preparedStatement.execute();
        } catch (SQLException e) {
            throw new HibernateException(MessageFormat.format("Could not alter JDBC connection to specified schema [{0}]", schemaName), e);
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return true;
    }

    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

}
