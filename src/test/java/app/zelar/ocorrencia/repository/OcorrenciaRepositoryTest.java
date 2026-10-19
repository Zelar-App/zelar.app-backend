package app.zelar.ocorrencia.repository;

import app.zelar.ocorrencia.dto.OcorrenciaResponse;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:zelar-ocorrencia;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OcorrenciaRepositoryTest {

    @Autowired
    private OcorrenciaRepository repository;
    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(CategoriaOcorrencia.class)
    void devePersistirEConsultarOcorrenciaComCadaCategoria(CategoriaOcorrencia categoria) {
        Usuario usuario = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        entityManager.persist(usuario);
        Ocorrencia ocorrencia = repository.saveAndFlush(new Ocorrencia(usuario, categoria,
                "Descrição de ocorrência", new Localizacao(-12.97, -38.50, "Praça central")));
        var id = ocorrencia.getId();
        entityManager.clear();

        Ocorrencia salva = repository.findById(id).orElseThrow();

        assertNotNull(id);
        assertEquals(categoria, salva.getCategoria());
        assertEquals(StatusOcorrencia.ENVIADA, salva.getStatus());
        assertEquals(usuario.getId(), salva.getUsuario().getId());
        assertEquals(-12.97, salva.getLocalizacao().getLatitude());
        assertEquals(-38.50, salva.getLocalizacao().getLongitude());
        assertEquals("Praça central", salva.getLocalizacao().getEnderecoAproximado());
        assertNotNull(salva.getDataCriacao());
        assertNull(salva.getDataAtualizacao());
        assertNull(salva.getPrioridade());
        assertEquals(categoria.name(), entityManager.createNativeQuery(
                "select categoria from ocorrencia where id = :id", String.class)
                .setParameter("id", id).getSingleResult());
    }

    @Test
    void deveConverterRespostaSemCarregarOuExporDadosDoUsuario() {
        Usuario usuario = new Usuario("João", "joao@email.com", "hash-secreto", PerfilUsuario.CIDADAO, "71999999999");
        entityManager.persist(usuario);
        Ocorrencia ocorrencia = repository.saveAndFlush(new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(0.0, 0.0, null)));
        var id = ocorrencia.getId();
        var usuarioId = usuario.getId();
        entityManager.clear();

        Ocorrencia salva = repository.findById(id).orElseThrow();
        assertFalse(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(salva, "usuario"));
        OcorrenciaResponse response = OcorrenciaResponse.from(salva);
        assertFalse(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(salva, "usuario"));
        assertEquals(usuarioId, response.usuarioId());
        assertEquals(id, response.id());
        assertEquals("Buraco na via", response.descricao());
        assertEquals(StatusOcorrencia.ENVIADA, response.status());
        assertNull(response.enderecoAproximado());

        var json = new ObjectMapper().valueToTree(response);
        assertTrue(json.has("usuarioId"));
        for (String campo : new String[]{"usuario", "nome", "email", "telefone", "senha"}) {
            assertFalse(json.has(campo));
        }
    }
}
