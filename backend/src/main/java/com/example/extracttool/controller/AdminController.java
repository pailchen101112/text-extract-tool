package com.example.extracttool.controller;

import com.example.extracttool.dto.*;
import com.example.extracttool.entity.Company;
import com.example.extracttool.entity.Position;
import com.example.extracttool.entity.SysMenu;
import com.example.extracttool.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/companies") @PreAuthorize("@authz.has('system:company:list') or @authz.has('system:position:list') or @authz.has('system:user:list')")
    public List<Company> companies() { return service.listCompanies(); }
    @PostMapping("/companies") @PreAuthorize("@authz.has('system:company:write')")
    public Company createCompany(@Valid @RequestBody CompanyRequest request) { return service.saveCompany(null, request); }
    @PutMapping("/companies/{id}") @PreAuthorize("@authz.has('system:company:write')")
    public Company updateCompany(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) { return service.saveCompany(id, request); }
    @DeleteMapping("/companies/{id}") @PreAuthorize("@authz.has('system:company:write')")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id) { service.deleteCompany(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/positions") @PreAuthorize("@authz.has('system:position:list') or @authz.has('system:user:list')")
    public List<Position> positions() { return service.listPositions(); }
    @PostMapping("/positions") @PreAuthorize("@authz.has('system:position:write')")
    public Position createPosition(@Valid @RequestBody PositionRequest request) { return service.savePosition(null, request); }
    @PutMapping("/positions/{id}") @PreAuthorize("@authz.has('system:position:write')")
    public Position updatePosition(@PathVariable Long id, @Valid @RequestBody PositionRequest request) { return service.savePosition(id, request); }
    @DeleteMapping("/positions/{id}") @PreAuthorize("@authz.has('system:position:write')")
    public ResponseEntity<Void> deletePosition(@PathVariable Long id) { service.deletePosition(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/menus") @PreAuthorize("@authz.has('system:menu:list') or @authz.has('system:role:list')")
    public List<SysMenu> menus() { return service.listMenus(); }
    @PostMapping("/menus") @PreAuthorize("@authz.has('system:menu:write')")
    public SysMenu createMenu(@Valid @RequestBody MenuRequest request) { return service.saveMenu(null, request); }
    @PutMapping("/menus/{id}") @PreAuthorize("@authz.has('system:menu:write')")
    public SysMenu updateMenu(@PathVariable Long id, @Valid @RequestBody MenuRequest request) { return service.saveMenu(id, request); }
    @DeleteMapping("/menus/{id}") @PreAuthorize("@authz.has('system:menu:write')")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) { service.deleteMenu(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/roles") @PreAuthorize("@authz.has('system:role:list') or @authz.has('system:user:list')")
    public List<Map<String, Object>> roles() { return service.listRoles(); }
    @PostMapping("/roles") @PreAuthorize("@authz.has('system:role:write')")
    public Map<String, Object> createRole(@Valid @RequestBody RoleRequest request) { return service.saveRole(null, request); }
    @PutMapping("/roles/{id}") @PreAuthorize("@authz.has('system:role:write')")
    public Map<String, Object> updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) { return service.saveRole(id, request); }
    @DeleteMapping("/roles/{id}") @PreAuthorize("@authz.has('system:role:write')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) { service.deleteRole(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/users") @PreAuthorize("@authz.has('system:user:list')")
    public List<Map<String, Object>> users() { return service.listUsers(); }
    @PostMapping("/users") @PreAuthorize("@authz.has('system:user:write')")
    public Map<String, Object> createUser(@Valid @RequestBody UserRequest request) { return service.saveUser(null, request); }
    @PutMapping("/users/{id}") @PreAuthorize("@authz.has('system:user:write')")
    public Map<String, Object> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) { return service.saveUser(id, request); }
    @PostMapping("/users/{id}/unlock") @PreAuthorize("@authz.has('system:user:write')")
    public ResponseEntity<Void> unlockUser(@PathVariable Long id) { service.unlockUser(id); return ResponseEntity.noContent().build(); }
    @DeleteMapping("/users/{id}") @PreAuthorize("@authz.has('system:user:write')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) { service.deleteUser(id); return ResponseEntity.noContent().build(); }
}
