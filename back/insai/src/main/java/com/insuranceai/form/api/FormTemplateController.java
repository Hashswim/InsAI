package com.insuranceai.form.api;

import com.insuranceai.common.web.ApiResponse;
import com.insuranceai.form.application.CreateTemplateCommand;
import com.insuranceai.form.application.CreateTemplateService;
import com.insuranceai.form.application.FormTemplateView;
import com.insuranceai.form.application.GetTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/templates")
public class FormTemplateController {

	private final CreateTemplateService createTemplateService;
	private final GetTemplateService getTemplateService;

	public FormTemplateController(CreateTemplateService createTemplateService,
	                              GetTemplateService getTemplateService) {
		this.createTemplateService = createTemplateService;
		this.getTemplateService = getTemplateService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<FormResponses.TemplateCreated> create(
			@Valid @RequestBody FormRequests.CreateTemplate request) {
		UUID id = createTemplateService.create(new CreateTemplateCommand(
				request.code(), request.name(), request.fields()));
		return ApiResponse.ok(new FormResponses.TemplateCreated(id));
	}

	@GetMapping("/{templateId}")
	public ApiResponse<FormTemplateView> get(@PathVariable UUID templateId) {
		return ApiResponse.ok(getTemplateService.get(templateId));
	}
}
