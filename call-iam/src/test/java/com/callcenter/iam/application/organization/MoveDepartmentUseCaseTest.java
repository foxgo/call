package com.callcenter.iam.application.organization;

import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoveDepartmentUseCaseTest {

    @Test
    void shouldCreateClosureRowsForNewChildDepartment() {
        InMemoryDepartmentTreeRepository repository = new InMemoryDepartmentTreeRepository();
        repository.seed(new Department(1L, 9L, null, "Root", "ACTIVE", 0));
        CreateDepartmentUseCase useCase = new CreateDepartmentUseCase(repository);

        useCase.execute(new CreateDepartmentCommand(2L, 9L, 1L, "Sales", "ACTIVE", 10));

        assertThat(repository.closureRows()).containsExactlyInAnyOrder(
                "9:1:1:0",
                "9:2:2:0",
                "9:1:2:1"
        );
    }

    @Test
    void shouldRejectMoveUnderOwnDescendant() {
        InMemoryDepartmentTreeRepository repository = new InMemoryDepartmentTreeRepository();
        repository.seed(new Department(1L, 9L, null, "Root", "ACTIVE", 0));
        repository.seed(new Department(2L, 9L, 1L, "Child", "ACTIVE", 10));
        repository.seed(new Department(3L, 9L, 2L, "Grandchild", "ACTIVE", 20));
        MoveDepartmentUseCase useCase = new MoveDepartmentUseCase(repository);

        assertThrows(DomainRuleViolationException.class, () -> useCase.execute(new MoveDepartmentCommand(9L, 1L, 3L)));
    }

    private static final class InMemoryDepartmentTreeRepository implements DepartmentTreeRepository {

        private final Map<Long, Department> departments = new HashMap<>();
        private final List<ClosureRow> closureRows = new ArrayList<>();

        void seed(Department department) {
            departments.put(department.getId(), department);
            rebuildClosure(department.getTenantId());
        }

        List<String> closureRows() {
            return closureRows.stream()
                    .map(row -> row.tenantId() + ":" + row.ancestorId() + ":" + row.descendantId() + ":" + row.depth())
                    .toList();
        }

        @Override
        public Department save(Department department) {
            departments.put(department.getId(), department);
            return department;
        }

        @Override
        public Optional<Department> findById(Long id) {
            return Optional.ofNullable(departments.get(id));
        }

        @Override
        public List<Department> findAll() {
            return departments.values().stream()
                    .sorted(java.util.Comparator.comparing(Department::getId))
                    .toList();
        }

        @Override
        public List<Department> findByTenantId(Long tenantId) {
            return departments.values().stream()
                    .filter(department -> tenantId.equals(department.getTenantId()))
                    .sorted(java.util.Comparator.comparing(Department::getId))
                    .toList();
        }

        @Override
        public boolean existsByTenantIdAndParentIdAndName(Long tenantId, Long parentId, String name) {
            return departments.values().stream().anyMatch(department ->
                    tenantId.equals(department.getTenantId())
                            && java.util.Objects.equals(parentId, department.getParentId())
                            && name.equals(department.getName()));
        }

        @Override
        public boolean isDescendant(Long tenantId, Long ancestorId, Long descendantId) {
            return closureRows.stream().anyMatch(row ->
                    tenantId.equals(row.tenantId())
                            && ancestorId.equals(row.ancestorId())
                            && descendantId.equals(row.descendantId())
                            && row.depth() > 0);
        }

        @Override
        public void deleteById(Long id) {
            departments.remove(id);
        }

        @Override
        public void rebuildClosure(Long tenantId) {
            closureRows.removeIf(row -> tenantId.equals(row.tenantId()));
            for (Department department : departments.values().stream()
                    .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                    .toList()) {
                closureRows.add(new ClosureRow(department.getTenantId(), department.getId(), department.getId(), 0));
                walkAncestors(department, department.getId(), 1);
            }
        }

        private void walkAncestors(Department current, Long descendantId, int depth) {
            if (current.getParentId() == null) {
                return;
            }
            Department parent = departments.get(current.getParentId());
            if (parent == null) {
                return;
            }
            closureRows.add(new ClosureRow(current.getTenantId(), parent.getId(), descendantId, depth));
            walkAncestors(parent, descendantId, depth + 1);
        }
    }

    private record ClosureRow(Long tenantId, Long ancestorId, Long descendantId, int depth) {
    }
}
