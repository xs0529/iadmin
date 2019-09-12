package xyz.iotcode.iadmin.demo.security.provider;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.iotcode.iadmin.common.exception.MyRuntimeException;
import xyz.iotcode.iadmin.common.redis.RedisService;
import xyz.iotcode.iadmin.demo.module.log.entity.SysLoginLog;
import xyz.iotcode.iadmin.demo.module.log.service.SysLoginLogService;
import xyz.iotcode.iadmin.demo.module.system.entity.SysPermission;
import xyz.iotcode.iadmin.demo.module.system.service.SysPermissionService;
import xyz.iotcode.iadmin.demo.security.bean.LoginDTO;
import xyz.iotcode.iadmin.demo.module.system.entity.SysRole;
import xyz.iotcode.iadmin.demo.module.system.entity.SysUser;
import xyz.iotcode.iadmin.demo.module.system.service.SysRoleService;
import xyz.iotcode.iadmin.demo.security.bean.LoginSuccessVO;
import xyz.iotcode.iadmin.permissions.bean.PermissionUser;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Administrator
 */
@Service
public class AuthenticationProviderImpl implements AuthenticationProvider {

    private static final long SEVEN_DAYS = 604800L;

    private static final long TWO_HOURS = 7200;

    /**
     * 乱输的，前盐，新增以及修改密码时不要忘了
     */
    public static final String BEGIN_SALT = "jkhjfghsjadhfjos";
    /**
     * 乱输的，后盐，新增以及修改密码时不要忘了
     */
    public static final String END_SALT = "mxojafwfjaisdof";

    @Autowired
    private RedisService redisService;
    @Autowired
    private SysRoleService sysRoleService;
    @Autowired
    private SysPermissionService sysPermissionService;
    @Autowired
    private SysLoginLogService sysLoginLogService;

    @Override
    public LoginSuccessVO login(LoginDTO dto) {
        long time;
        SysUser sysUser = new SysUser().selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())
                .eq(SysUser::getPassword, SecureUtil.md5(BEGIN_SALT+dto.getPassword().trim()+END_SALT)));
        if (sysUser==null){
            throw new MyRuntimeException("用户名或者密码错误");
        }
        if (sysUser.getState()==0){
            throw new MyRuntimeException("账户已被禁用");
        }
        if (dto.getSaveLogin()!=null&&dto.getSaveLogin()){
            time = SEVEN_DAYS;
        }else {
            time = TWO_HOURS;
        }
        PermissionUser user = new PermissionUser();
        BeanUtils.copyProperties(sysUser, user);
        user.setUserId(sysUser.getUserId());
        List<SysRole> roles = sysRoleService.getByUserId(sysUser.getUserId());
        if (CollectionUtil.isNotEmpty(roles)){
            user.setRoles(roles.stream().map(SysRole::getLabel).collect(Collectors.toList()));
        }
        Set<SysPermission> permissions = sysPermissionService.getByUserId(sysUser.getUserId());
        if (CollectionUtil.isNotEmpty(permissions)){
            user.setPermissions(permissions.stream().map(SysPermission::getPermissionCode).collect(Collectors.toList()));
        }
        String s = UUID.randomUUID().toString();
        redisService.set(s, user, time);

        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUsername(dto.getUsername());
        sysLoginLogService.isave(loginLog);

        LoginSuccessVO vo = new LoginSuccessVO();
        vo.setPermissionCodes(user.getPermissions());
        vo.setUserInfo(sysUser);
        vo.setPermissions(permissions);
        vo.setRoles(roles);
        vo.setToken(s);
        return vo;
    }

    @Override
    public PermissionUser getUserByToken(String token) {
        PermissionUser user = (PermissionUser) redisService.get(token);
        if (user==null){
            throw new MyRuntimeException("token无效");
        }
        return user;
    }
}
