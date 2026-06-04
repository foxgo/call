package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.callcenter.iam.domain.audit.AuditLog;
import com.callcenter.iam.infrastructure.audit.AuditLogQuery;
import com.callcenter.iam.infrastructure.audit.AuditLogRepository;
import com.callcenter.iam.infrastructure.persistence.dataobject.AuditLogDO;
import com.callcenter.iam.infrastructure.persistence.mapper.AuditLogMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAuditLogRepository implements AuditLogRepository {

    private final AuditLogMapper auditLogMapper;

    public MybatisAuditLogRepository(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        AuditLogDO dataObject = toDataObject(auditLog);
        if (auditLogMapper.selectById(auditLog.getId()) == null) {
            auditLogMapper.insert(dataObject);
        } else {
            auditLogMapper.updateById(dataObject);
        }
        return auditLog;
    }

    @Override
    public List<AuditLog> query(AuditLogQuery query) {
        LambdaQueryWrapper<AuditLogDO> wrapper = new LambdaQueryWrapper<AuditLogDO>()
                .eq(query.tenantId() != null, AuditLogDO::getTenantId, query.tenantId())
                .eq(query.operatorId() != null, AuditLogDO::getOperatorId, query.operatorId())
                .eq(query.resourceType() != null, AuditLogDO::getResourceType, query.resourceType())
                .eq(query.resourceId() != null, AuditLogDO::getResourceId, query.resourceId())
                .orderByDesc(AuditLogDO::getCreatedAt, AuditLogDO::getId);
        return auditLogMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AuditLog> findById(Long id) {
        return Optional.ofNullable(auditLogMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public long nextId() {
        return auditLogMapper.selectList(null).stream()
                .mapToLong(log -> log.getId() == null ? 0L : log.getId())
                .max()
                .orElse(0L) + 1;
    }

    private AuditLogDO toDataObject(AuditLog auditLog) {
        AuditLogDO dataObject = new AuditLogDO();
        dataObject.setId(auditLog.getId());
        dataObject.setTenantId(auditLog.getTenantId());
        dataObject.setOperatorId(auditLog.getOperatorId());
        dataObject.setAction(auditLog.getAction());
        dataObject.setResourceType(auditLog.getResourceType());
        dataObject.setResourceId(auditLog.getResourceId());
        dataObject.setCreatedAt(auditLog.getCreatedAt());
        return dataObject;
    }

    private AuditLog toDomain(AuditLogDO dataObject) {
        return new AuditLog(
                dataObject.getId(),
                dataObject.getTenantId(),
                dataObject.getOperatorId(),
                dataObject.getAction(),
                dataObject.getResourceType(),
                dataObject.getResourceId(),
                dataObject.getCreatedAt()
        );
    }
}
