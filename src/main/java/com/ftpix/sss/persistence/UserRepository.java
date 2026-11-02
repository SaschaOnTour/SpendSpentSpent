package com.ftpix.sss.persistence;

import com.ftpix.sss.models.User;
import com.ftpix.sss.models.Household;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("from User u where u.email = :email")
    User findFirstByEmail(@Param("email") String email);

    @Query("from User u where u.oidcSub = :sub")
    User findFirstByOidcSub(@Param("sub") String oidcSub);

    User findFirstById(UUID id);

    long countByEmail(String email);

    /**
     * The user and the accepted members of the user's household, ordered so locks are always taken in the same order.
     */
    String HOUSEHOLD_USERS = """
            select u from User u where u = :user or u in (
                select hm.user from HouseholdMember hm
                where hm.status = com.ftpix.sss.models.HouseholdInviteStatus.accepted
                and hm.household in (
                    select hm2.household from HouseholdMember hm2
                    where hm2.user = :user
                    and hm2.status = com.ftpix.sss.models.HouseholdInviteStatus.accepted
                )
            )
            order by u.id
            """;

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(HOUSEHOLD_USERS)
    List<User> lockHouseholdUsersShared(@Param("user") User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(HOUSEHOLD_USERS)
    List<User> lockHouseholdUsersExclusive(@Param("user") User user);

    /**
     * The user, the accepted members of the user's household and the accepted members of the household the user joins.
     */
    String JOINING_USERS = """
            select u from User u where u = :user or u in (
                select hm.user from HouseholdMember hm
                where hm.status = com.ftpix.sss.models.HouseholdInviteStatus.accepted
                and (hm.household = :household or hm.household in (
                    select hm2.household from HouseholdMember hm2
                    where hm2.user = :user
                    and hm2.status = com.ftpix.sss.models.HouseholdInviteStatus.accepted
                ))
            )
            order by u.id
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(JOINING_USERS)
    List<User> lockJoiningUsersExclusive(@Param("user") User user, @Param("household") Household household);
}
