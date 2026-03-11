package cn.bugstack.ai.config;

import com.zaxxer.hikari.HikariDataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
public class AiAgentConfig {

    /**
     * 为 MyBatis 创建主数据源
     */
    @Bean("mybatisDataSource")
    @Primary
    public DataSource mybatisDataSource(@Value("${spring.datasource.mysql.driver-class-name}") String driverClassName,
                                        @Value("${spring.datasource.mysql.url}") String url,
                                        @Value("${spring.datasource.mysql.username}") String username,
                                        @Value("${spring.datasource.mysql.password}") String password,
                                        @Value("${spring.datasource.mysql.hikari.maximum-pool-size:10}") int maximumPoolSize,
                                        @Value("${spring.datasource.mysql.hikari.minimum-idle:5}") int minimumIdle,
                                        @Value("${spring.datasource.mysql.hikari.idle-timeout:30000}") long idleTimeout,
                                        @Value("${spring.datasource.mysql.hikari.connection-timeout:30000}") long connectionTimeout,
                                        @Value("${spring.datasource.mysql.hikari.max-lifetime:1800000}") long maxLifetime) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        // 连接池配置
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        dataSource.setPoolName("MainHikariPool");
        return dataSource;
    }

    /**
     * 配置 MyBatis 的 SqlSessionFactory
     */
    @Bean("sqlSessionFactory")
    public SqlSessionFactoryBean sqlSessionFactory(@Qualifier("mybatisDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(dataSource);

        // 设置MyBatis配置文件位置
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sqlSessionFactoryBean.setConfigLocation(resolver.getResource("classpath:/mybatis/config/mybatis-config.xml"));

        // 设置Mapper XML文件位置
        sqlSessionFactoryBean.setMapperLocations(resolver.getResources("classpath:/mybatis/mapper/*.xml"));

        return sqlSessionFactoryBean;
    }

    /**
     * 配置 SqlSessionTemplate
     */
    @Bean("sqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) throws Exception {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 为 PgVector 创建专用的数据源
     */
    @Bean("pgVectorDataSource")
    public DataSource pgVectorDataSource(@Value("${spring.datasource.pgvector.driver-class-name}") String driverClassName,
                                         @Value("${spring.datasource.pgvector.url}") String url,
                                         @Value("${spring.datasource.pgvector.username}") String username,
                                         @Value("${spring.datasource.pgvector.password}") String password,
                                         @Value("${spring.datasource.pgvector.hikari.maximum-pool-size:5}") int maximumPoolSize,
                                         @Value("${spring.datasource.pgvector.hikari.minimum-idle:2}") int minimumIdle,
                                         @Value("${spring.datasource.pgvector.hikari.idle-timeout:30000}") long idleTimeout,
                                         @Value("${spring.datasource.pgvector.hikari.connection-timeout:30000}") long connectionTimeout) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        // 连接池配置
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setConnectionTimeout(connectionTimeout);
        // 确保在启动时连接数据库
        dataSource.setInitializationFailTimeout(1);  // 设置为1ms，如果连接失败则快速失败
        dataSource.setConnectionTestQuery("SELECT 1"); // 简单的连接测试查询
        dataSource.setAutoCommit(true);
        dataSource.setPoolName("PgVectorHikariPool");
        return dataSource;
    }

    /**
     * 为 PgVector 创建专用的 JdbcTemplate
     */
    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * -- 删除旧的表（如果存在）
     * DROP TABLE IF EXISTS public.vector_store_openai;
     * <p>
     * -- 创建新的表，使用UUID作为主键
     * CREATE TABLE public.vector_store_openai (
     * id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     * content TEXT NOT NULL,
     * metadata JSONB,
     * embedding VECTOR(1536)
     * );
     * <p>
     * SELECT * FROM vector_store_openai
     */
    @Bean("vectorStore")
    public PgVectorStore pgVectorStore(
            @Value("${spring.ai.zhipuai.api-key}") String apiKey,
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {

        String baseUrl = "https://open.bigmodel.cn/api/paas/v4/";
        // 1. 创建智谱 AI 的原生 API 客户端
        ZhiPuAiApi zhiPuAiApi = new ZhiPuAiApi(baseUrl, apiKey);

        // 2. 创建嵌入模型（Embedding Model）
        // 默认使用的是 embedding-3，它的维度通常是 1024
        ZhiPuAiEmbeddingModel zhiPuAiEmbeddingModel = new ZhiPuAiEmbeddingModel(zhiPuAiApi);

        // 3. 构建 PgVectorStore
        return PgVectorStore.builder(jdbcTemplate, zhiPuAiEmbeddingModel)
                .vectorTableName("vector_store_zhipu") // 建议根据模型换个表名，防止维度冲突
                .dimensions(1024)                // 🌟 关键：显式指定维度，智谱 embedding-3 是 1024
                .initializeSchema(true)          // 如果表不存在，自动创建
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
