package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "tenants")
class Tenant(

	@Column(nullable = false)
	var name: String,

	@Column(nullable = false, unique = true)
	var slug: String,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
