package com.company.attendance.security;

import com.company.attendance.entity.User;
import com.company.attendance.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetailsService for Spring Security authentication.
 *
 * Loads user information from the database and converts it to Spring Security UserDetails
 * format with role-based authorities.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserService userService;

	@Override
	@Transactional
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userService.findByUsername(username)
			.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

		return UserPrincipal.create(user);
	}

	/**
	 * Custom UserDetails implementation.
	 */
	public static class UserPrincipal implements UserDetails {

		private final String username;

		private final String password;

		private final boolean active;

		private final Collection<? extends GrantedAuthority> authorities;

		public UserPrincipal(String username, String password, boolean active,
				Collection<? extends GrantedAuthority> authorities) {
			this.username = username;
			this.password = password;
			this.active = active;
			this.authorities = authorities;
		}

		public static UserPrincipal create(User user) {
			Collection<GrantedAuthority> authorities = Collections
				.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

			return new UserPrincipal(user.getUsername(), user.getPasswordHash(), user.getIsActive(), authorities);
		}

		@Override
		public Collection<? extends GrantedAuthority> getAuthorities() {
			return authorities;
		}

		@Override
		public String getPassword() {
			return password;
		}

		@Override
		public String getUsername() {
			return username;
		}

		@Override
		public boolean isAccountNonExpired() {
			return true;
		}

		@Override
		public boolean isAccountNonLocked() {
			return true;
		}

		@Override
		public boolean isCredentialsNonExpired() {
			return true;
		}

		@Override
		public boolean isEnabled() {
			return active;
		}

	}

}
