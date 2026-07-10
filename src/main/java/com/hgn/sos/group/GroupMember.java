package com.hgn.sos.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "group_member")
@IdClass(GroupMember.GroupMemberId.class)
public class GroupMember {

    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Id
    @Column(name = "trekker_id", nullable = false)
    private UUID trekkerId;

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    public UUID getTrekkerId() { return trekkerId; }
    public void setTrekkerId(UUID trekkerId) { this.trekkerId = trekkerId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public static class GroupMemberId implements Serializable {
        private UUID groupId;
        private UUID trekkerId;
        private UUID orderId;

        public GroupMemberId() {}

        public GroupMemberId(UUID groupId, UUID trekkerId, UUID orderId) {
            this.groupId = groupId;
            this.trekkerId = trekkerId;
            this.orderId = orderId;
        }

        public UUID getGroupId() { return groupId; }
        public UUID getTrekkerId() { return trekkerId; }
        public UUID getOrderId() { return orderId; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GroupMemberId that)) return false;
            return groupId.equals(that.groupId) && trekkerId.equals(that.trekkerId) && orderId.equals(that.orderId);
        }

        @Override
        public int hashCode() {
            int result = groupId.hashCode();
            result = 31 * result + trekkerId.hashCode();
            result = 31 * result + orderId.hashCode();
            return result;
        }
    }
}