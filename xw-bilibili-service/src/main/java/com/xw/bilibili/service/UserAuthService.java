package com.xw.bilibili.service;

import com.xw.bilibili.domain.auth.*;
import com.xw.bilibili.domain.constant.AuthRoleConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAuthService {

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private AuthRoleService authRoleService;
    public UserAuthorities getUserAuthorities(Long userId) {
        //查询user对应的角色
        //一个用户可能有多个角色，所以结果是一个列表
        //这个方法涉及角色和user的关系，所以放在userRoleService中
        List<UserRole> userRoleList = userRoleService.getUserRoleByUserId(userId);
        //查询上面查出来的角色对应的id，方便后面与权限表关联
        Set<Long> roleIdSet = userRoleList.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        //查询页面访问权限和按钮操作权限分别关联的列表
        //按钮操作权限
        //查询角色id对应的用户页面元素操作权限
        List<AuthRoleElementOperation> roleElementOperationList = authRoleService.getRoleElementOperationsByRoleIds(roleIdSet);
        //查询角色id对应的用户菜单操作权限
        List<AuthRoleMenu> authRoleMenuList = authRoleService.getAuthRoleMenusByRoleIds(roleIdSet);
        UserAuthorities userAuthorities = new UserAuthorities();
        userAuthorities.setRoleElementOperationList(roleElementOperationList);
        userAuthorities.setRoleMenuList(authRoleMenuList);
        return userAuthorities;
    }

    public void addUserDefaultRole(Long id) {
        UserRole userRole = new UserRole();
        //默认设置为Lv0，且通过编码获取角色
        AuthRole role = authRoleService.getRoleByCode(AuthRoleConstant.ROLE_LV0);
        //用role给userRole赋值
        userRole.setUserId(id);
        userRole.setRoleId(role.getId());
        //把user和角色的对应关系添加到数据库
        userRoleService.addUserRole(userRole);
    }
}
