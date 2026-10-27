package app.zelar.ocorrencia;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("test")
@Transactional
class AnexoMigrationIntegrationTest {

    private static final String URL = System.getenv().getOrDefault("DB09_TEST_URL",
            "jdbc:h2:mem:zelar-anexo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    private static final String USUARIO = System.getenv().getOrDefault("DB09_TEST_USERNAME", "sa");
    private static final String SENHA = System.getenv().getOrDefault("DB09_TEST_PASSWORD", "");
    private static final String INSERT_ANEXO = """
            INSERT INTO anexo (id, ocorrencia_id, bucket, chave_objeto, tipo_conteudo,
                tamanho_bytes, largura, altura, estado_upload, data_criacao, data_atualizacao)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private Flyway flyway;

    private UUID usuarioId;
    private UUID ocorrenciaId;

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USUARIO);
        registry.add("spring.datasource.password", () -> SENHA);
        registry.add("spring.datasource.driver-class-name",
                () -> URL.startsWith("jdbc:postgresql:") ? "org.postgresql.Driver" : "org.h2.Driver");
        registry.add("admin.email", () -> "");
        registry.add("admin.password", () -> "");
    }

    @BeforeEach
    void prepararOcorrencia() {
        usuarioId = UUID.randomUUID();
        ocorrenciaId = UUID.randomUUID();
        criarUsuarioEOcorrencia("", usuarioId, ocorrenciaId);
    }

    @Test
    void deveAplicarMigrationIncrementalComSchemaValidadoSemGuardarBinarioOuCredencial() {
        flyway.validate();
        var versoes = Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion()).toList();
        assertTrue(versoes.containsAll(List.of("1", "2", "3", "4", "5")));
        var colunas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE LOWER(table_name) = 'anexo' AND LOWER(table_schema) = 'public'
                """, String.class).stream().map(String::toLowerCase).collect(Collectors.toSet());
        assertEquals(dadosValidos().keySet(), colunas);
    }

    @Test
    void devePreservarOcorrenciaSemFotografia() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ocorrencia WHERE id = ?",
                Integer.class, ocorrenciaId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM anexo WHERE ocorrencia_id = ?",
                Integer.class, ocorrenciaId));
    }

    @Test
    void deveReservarVinculoPendenteComDatasPadrao() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO anexo (id, ocorrencia_id, bucket, chave_objeto, tipo_conteudo,
                    tamanho_bytes, largura, altura) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, ocorrenciaId, "ocorrencias-fotografias", UUID.randomUUID() + ".png",
                "image/png", 100L, 10, 8);
        assertEquals("PENDENTE", jdbc.queryForObject("SELECT estado_upload FROM anexo WHERE id = ?",
                String.class, id));
        assertNotNull(jdbc.queryForObject("SELECT data_criacao FROM anexo WHERE id = ?", Timestamp.class, id));
        assertNotNull(jdbc.queryForObject("SELECT data_atualizacao FROM anexo WHERE id = ?", Timestamp.class, id));
    }

    @ParameterizedTest
    @CsvSource({"image/png,png,PENDENTE", "image/png,png,DISPONIVEL", "image/png,png,LIMPEZA_PENDENTE",
            "image/jpeg,jpg,PENDENTE", "image/jpeg,jpg,DISPONIVEL", "image/jpeg,jpg,LIMPEZA_PENDENTE"})
    void deveAceitarFormatosEEstadosDoContrato(String tipo, String extensao, String estado) {
        var dados = dadosValidos();
        dados.put("tipo_conteudo", tipo);
        dados.put("chave_objeto", UUID.randomUUID() + "." + extensao);
        dados.put("estado_upload", estado);
        assertEquals(1, inserir(dados));
    }

    @ParameterizedTest
    @CsvSource({"1,1,1", "5242880,4000,4000", "100,8192,1", "100,1,8192"})
    void deveAceitarLimitesInclusivos(long tamanho, int largura, int altura) {
        var dados = dadosValidos();
        dados.put("tamanho_bytes", tamanho);
        dados.put("largura", largura);
        dados.put("altura", altura);
        assertEquals(1, inserir(dados));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "ocorrencia_id", "bucket", "chave_objeto", "tipo_conteudo",
            "tamanho_bytes", "largura", "altura", "estado_upload", "data_criacao", "data_atualizacao"})
    void deveRecusarMetadadoObrigatorioNulo(String coluna) throws Exception {
        var dados = dadosValidos();
        dados.put(coluna, null);
        deveRejeitar(() -> inserir(dados));
    }

    @ParameterizedTest
    @CsvSource({"bucket,../outro", "bucket,''", "bucket,bucket com espaco", "chave_objeto,../foto.png",
            "chave_objeto,foto.png", "chave_objeto,12345678-1234-4234-8234-123456789abc.svg",
            "chave_objeto,12345678-1234-4234-8234-123456789abc.jpg", "tipo_conteudo,image/webp",
            "tipo_conteudo,''", "estado_upload,FALSO", "estado_upload,''"})
    void deveRecusarFormatoCaminhoOuEstadoForaDoContrato(String coluna, String valor) throws Exception {
        var dados = dadosValidos();
        dados.put(coluna, valor);
        deveRejeitar(() -> inserir(dados));
    }

    @ParameterizedTest
    @CsvSource({"tamanho_bytes,0", "tamanho_bytes,-1", "tamanho_bytes,5242881", "largura,0",
            "largura,-1", "largura,8193", "altura,0", "altura,-1", "altura,8193"})
    void deveRecusarTamanhoOuDimensaoForaDoLimite(String coluna, long valor) throws Exception {
        var dados = dadosValidos();
        dados.put(coluna, valor);
        deveRejeitar(() -> inserir(dados));
    }

    @Test
    void deveRecusarAreaSuperiorADezesseisMilhoesDePixels() throws Exception {
        var dados = dadosValidos();
        dados.put("largura", 5000);
        dados.put("altura", 4000);
        deveRejeitar(() -> inserir(dados));
    }

    @Test
    void deveRecusarVinculoComOcorrenciaInexistente() throws Exception {
        var dados = dadosValidos();
        dados.put("ocorrencia_id", UUID.randomUUID());
        deveRejeitar(() -> inserir(dados));
    }

    @Test
    void devePermitirUmaUnicaFotografiaPorOcorrencia() throws Exception {
        inserir(dadosValidos());
        deveRejeitar(() -> inserir(dadosValidos()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM anexo WHERE ocorrencia_id = ?",
                Integer.class, ocorrenciaId));
    }

    @Test
    void deveImpedirMesmoObjetoVinculadoADuasOcorrenciasNoMesmoBucket() throws Exception {
        var primeiro = dadosValidos();
        inserir(primeiro);
        var segundo = dadosValidos();
        segundo.put("ocorrencia_id", outraOcorrencia());
        segundo.put("chave_objeto", primeiro.get("chave_objeto"));
        deveRejeitar(() -> inserir(segundo));
    }

    @Test
    void deveDistinguirObjetosComMesmaChaveEmBucketsDiferentes() {
        var primeiro = dadosValidos();
        inserir(primeiro);
        var segundo = dadosValidos();
        segundo.put("ocorrencia_id", outraOcorrencia());
        segundo.put("chave_objeto", primeiro.get("chave_objeto"));
        segundo.put("bucket", "outro-bucket");
        assertEquals(1, inserir(segundo));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDENTE", "DISPONIVEL", "LIMPEZA_PENDENTE"})
    void deveImpedirExclusaoEmCascataDaOcorrenciaComAnexo(String estado) throws Exception {
        var dados = dadosValidos();
        dados.put("estado_upload", estado);
        inserir(dados);
        deveRejeitar(() -> jdbc.update("DELETE FROM ocorrencia WHERE id = ?", ocorrenciaId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM anexo WHERE ocorrencia_id = ?",
                Integer.class, ocorrenciaId));
    }

    @Test
    void devePersistirEstadoDeRecuperacaoSemPerderAChaveDoObjeto() {
        var dados = dadosValidos();
        inserir(dados);
        jdbc.update("UPDATE anexo SET estado_upload = 'LIMPEZA_PENDENTE', data_atualizacao = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), dados.get("id"));
        assertEquals(dados.get("chave_objeto"), jdbc.queryForObject(
                "SELECT chave_objeto FROM anexo WHERE id = ?", String.class, dados.get("id")));
        assertEquals("LIMPEZA_PENDENTE", jdbc.queryForObject(
                "SELECT estado_upload FROM anexo WHERE id = ?", String.class, dados.get("id")));
    }

    @Test
    void deveAtualizarBancoAnteriorPreservandoOcorrenciaSemFoto() {
        String schema = "db09_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String prefixo = "\"" + schema + "\".";
        try (HikariDataSource conexoesUpgrade = new HikariDataSource()) {
            conexoesUpgrade.setJdbcUrl(URL);
            conexoesUpgrade.setUsername(USUARIO);
            conexoesUpgrade.setPassword(SENHA);
            conexoesUpgrade.setMaximumPoolSize(5);
            JdbcTemplate bancoUpgrade = new JdbcTemplate(conexoesUpgrade);
            Flyway anterior = Flyway.configure().dataSource(conexoesUpgrade).schemas(schema)
                    .locations("classpath:db/migration").target("4").load();
            try {
                anterior.migrate();
                UUID autor = UUID.randomUUID();
                UUID ocorrencia = UUID.randomUUID();
                criarUsuarioEOcorrencia(bancoUpgrade, prefixo, autor, ocorrencia);
                Flyway atual = Flyway.configure().dataSource(conexoesUpgrade).schemas(schema)
                        .locations("classpath:db/migration").load();
                assertEquals(1, atual.migrate().migrationsExecuted);
                atual.validate();
                assertEquals(0, atual.migrate().migrationsExecuted);
                assertEquals(1, bancoUpgrade.queryForObject("SELECT COUNT(*) FROM " + prefixo + "ocorrencia WHERE id = ?",
                        Integer.class, ocorrencia));
                assertEquals(0, bancoUpgrade.queryForObject("SELECT COUNT(*) FROM " + prefixo + "anexo", Integer.class));
            } finally {
                bancoUpgrade.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            }
        }
    }

    private UUID outraOcorrencia() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ocorrencia (id, descricao, status, categoria, latitude, longitude, usuario_id)
                VALUES (?, 'Outro registro de teste', 'ENVIADA', 'BURACO', 0, 0, ?)
                """, id, usuarioId);
        return id;
    }

    private void criarUsuarioEOcorrencia(String prefixo, UUID autor, UUID ocorrencia) {
        criarUsuarioEOcorrencia(jdbc, prefixo, autor, ocorrencia);
    }

    private void criarUsuarioEOcorrencia(JdbcTemplate banco, String prefixo, UUID autor, UUID ocorrencia) {
        banco.update("INSERT INTO " + prefixo + "usuario (id, nome, email, senha_hash, papel) VALUES (?, ?, ?, ?, ?)",
                autor, "Usuário de teste", autor + "@example.com", "hash-exclusivo-de-teste", "CIDADAO");
        banco.update("INSERT INTO " + prefixo + "ocorrencia "
                        + "(id, descricao, status, categoria, latitude, longitude, usuario_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ocorrencia, "Ocorrência de teste", "ENVIADA", "BURACO", 0, 0, autor);
    }

    private Map<String, Object> dadosValidos() {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("id", UUID.randomUUID());
        dados.put("ocorrencia_id", ocorrenciaId);
        dados.put("bucket", "ocorrencias-fotografias");
        dados.put("chave_objeto", UUID.randomUUID() + ".png");
        dados.put("tipo_conteudo", "image/png");
        dados.put("tamanho_bytes", 100L);
        dados.put("largura", 10);
        dados.put("altura", 8);
        dados.put("estado_upload", "PENDENTE");
        dados.put("data_criacao", Timestamp.valueOf(LocalDateTime.now()));
        dados.put("data_atualizacao", Timestamp.valueOf(LocalDateTime.now()));
        return dados;
    }

    private int inserir(Map<String, Object> dados) {
        return jdbc.update(INSERT_ANEXO, dados.values().toArray());
    }

    private void deveRejeitar(Runnable operacao) throws Exception {
        Connection conexao = DataSourceUtils.getConnection(dataSource);
        Savepoint ponto = conexao.setSavepoint();
        try {
            assertThrows(DataIntegrityViolationException.class, operacao::run);
        } finally {
            conexao.rollback(ponto);
            conexao.releaseSavepoint(ponto);
        }
    }
}
